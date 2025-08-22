
---

# README.md

# Frontend & Backend Java App Deployment on Kubernetes (EKS)

---

## **1️⃣ Prerequisites**

* **Java JDK 17** installed

  ```bash
  java -version
  ```
* **Apache Tomcat 10.1** installed

  * Windows default: `C:\Program Files\Apache Software Foundation\Tomcat 10.1\`
  * Linux default: `/usr/local/tomcat/`
* **MySQL Server** (deployed in Kubernetes or local)
* **MySQL Connector/J** (`mysql-connector-j-9.1.0.jar`)
* **Docker** installed
* **AWS CLI** configured for ECR
* **kubectl** configured for EKS cluster
* **wait-for-it.sh** script (to wait for MySQL readiness)

---

## **2️⃣ Project Structure**

```
project/
│
├─ LoginServlet.java
├─ RegisterServlet.java
├─ index.html
├─ profile.jsp
├─ WEB-INF/
│   ├─ classes/    <- compiled .class files
│   └─ lib/        <- mysql-connector-j-9.1.0.jar
├─ java-test.war    <- final WAR file
├─ Dockerfile-frontend
├─ Dockerfile-backend
└─ wait-for-it.sh
```

---

## **3️⃣ Compile Java Servlets**

### **Windows**

1. Open **PowerShell** in project folder
2. Compile servlets with `servlet-api` and MySQL connector:

```powershell
javac -cp "C:\Program Files\Apache Software Foundation\Tomcat 10.1\lib\servlet-api.jar;D:\libs\mysql-connector-j-9.1.0.jar;." LoginServlet.java RegisterServlet.java
```

3. Move compiled classes to `WEB-INF/classes/`:

```powershell
mkdir -p WEB-INF\classes
move *.class WEB-INF\classes\
```

### **Linux**

```bash
# Inside project folder
javac -cp "/usr/local/tomcat/lib/servlet-api.jar:/usr/local/tomcat/lib/mysql-connector-j-9.1.0.jar:." LoginServlet.java RegisterServlet.java

# Move classes
mkdir -p WEB-INF/classes
mv *.class WEB-INF/classes/
```

* **Explanation**: `-cp` specifies the classpath with servlet API and MySQL connector.
* **Output**: `.class` files inside `WEB-INF/classes/`.

---

## **4️⃣ Generate WAR File**

```bash
# Linux & Windows
jar -cvf java-test.war *
```

* WAR file includes:

  * `WEB-INF/classes` → compiled servlets
  * `WEB-INF/lib` → mysql-connector-j-9.1.0.jar
  * HTML/JSP pages

---

## **5️⃣ Docker Image Build & Push**

### **Frontend**

1. Login to AWS ECR:

```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 180294213274.dkr.ecr.us-east-1.amazonaws.com
```

2. Build Docker image:

```bash
docker build -f Dockerfile-frontend -t app001-dev:frontend1.1.0 .
```

3. Tag image for ECR:

```bash
docker tag app001-dev:frontend1.1.0 180294213274.dkr.ecr.us-east-1.amazonaws.com/app001-dev:frontend1.1.0
```

4. Push to ECR:

```bash
docker push 180294213274.dkr.ecr.us-east-1.amazonaws.com/app001-dev:frontend1.1.0
```

### **Backend**

Repeat the same steps using `Dockerfile-backend` and tag as `backend1.1.0`.

---

## **6️⃣ Kubernetes Manifests Overview**

### **6.1 Cleanup Job for MySQL hostPath**

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: cleanup-mysql-data
spec:
  template:
    spec:
      containers:
      - name: cleanup
        image: busybox
        command:
          - sh
          - -c
          - |
            echo "Cleaning MySQL hostPath directory..."
            rm -rf /host-mysql-data
            mkdir -p /host-mysql-data
            echo "Done."
        volumeMounts:
        - name: mysql-data
          mountPath: /host-mysql-data
      restartPolicy: Never
      volumes:
      - name: mysql-data
        hostPath:
          path: /home/ubuntu/mysql-data
          type: DirectoryOrCreate
      securityContext:
        runAsUser: 0
        runAsGroup: 0
        fsGroup: 0
  backoffLimit: 0
```

* **Storage Type**: `hostPath` – persists MySQL data on the host at `/home/ubuntu/mysql-data`
* **Reason**: Keep MySQL data even if pod restarts.

---

### **6.2 MySQL Deployment & Service**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
      - name: mysql
        image: 180294213274.dkr.ecr.us-east-1.amazonaws.com/app001-dev:backend1.1.0
        ports:
        - containerPort: 3306
        env:
        - name: MYSQL_ROOT_PASSWORD
          value: "Login%12345"
        - name: MYSQL_DATABASE
          value: "db1"
        volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
      volumes:
      - name: mysql-storage
        hostPath:
          path: /home/ubuntu/mysql-data
          type: DirectoryOrCreate
---
apiVersion: v1
kind: Service
metadata:
  name: mysql-service1
spec:
  selector:
    app: mysql
  ports:
  - protocol: TCP
    port: 3306
    targetPort: 3306
  type: ClusterIP
```

---

### **6.3 Frontend Deployment & NodePort**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: java-frontend
  template:
    metadata:
      labels:
        app: java-frontend
    spec:
      containers:
      - name: frontend
        image: 180294213274.dkr.ecr.us-east-1.amazonaws.com/app001-dev:frontend1.1.0
        ports:
        - containerPort: 8080
        env:
        - name: DB_HOST
          value: "mysql-service1"
        - name: DB_PORT
          value: "3306"
        - name: DB_NAME
          value: "db1"
        - name: DB_USER
          value: "root"
        - name: DB_PASSWORD
          value: "Login%12345"
---
apiVersion: v1
kind: Service
metadata:
  name: frontend-nodeport
spec:
  selector:
    app: java-frontend
  ports:
  - protocol: TCP
    port: 8080
    targetPort: 8080
    nodePort: 30080
  type: NodePort
```

---

## **7️⃣ Deploy to EKS**

```bash
# Apply all manifests
kubectl apply -f k8s-manifest.yaml

# Verify pods
kubectl get pods

# Verify services
kubectl get svc
```

---

## **8️⃣ Test MySQL Connection**

Inside the frontend container:

```bash
java -cp ".:/usr/local/tomcat/lib/mysql-connector-j-9.1.0.jar" TestDB
# Output: Connected!
```

---

## **9️⃣ Build Flow Diagram (Text-Based)**

```
+--------------------+
| Frontend NodePort  |  <-- Tomcat + Servlets
| Port 30080         |
+---------+----------+
          |
          v
+--------------------+
| MySQL Service      |  <-- ClusterIP: mysql-service1:3306
+--------------------+
          ^
          |
+--------------------+
| MySQL Deployment   |  <-- HostPath storage: /home/ubuntu/mysql-data
+--------------------+
```

---

## **🔟 Notes & Tips**

1. Always **compile servlets** after code changes before WAR generation.
2. Ensure **WAR file is updated** in Docker image.
3. Use `wait-for-it.sh` to ensure MySQL is up before Tomcat starts.
4. Check MySQL connection inside container:

```bash
java -cp ".:/usr/local/tomcat/lib/mysql-connector-j-9.1.0.jar" TestDB
```

5. Handle special characters (`%`) in MySQL passwords carefully; URL encode if necessary.

---

This README covers **Windows & Linux compilation**, **WAR generation**, **Docker build & push**, **Kubernetes deployment**, **storage explanation**, and **frontend-backend flow diagram**.

---

If you want, I can also provide a **ready-to-run automation script** that compiles, generates WAR, builds Docker, tags, and pushes in one shot for both Windows & Linux.

Do you want me to create that script as well?
