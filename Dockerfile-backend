# Use official MySQL base image
FROM mysql:8.0

# Set environment variables
ENV MYSQL_ROOT_PASSWORD=Login%12345
ENV MYSQL_DATABASE=db1

# Copy initialization SQL
COPY init.sql /docker-entrypoint-initdb.d/

# Expose MySQL port
EXPOSE 3306

