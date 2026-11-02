

1. **Start MySQL** in the XAMPP Control Panel.
2. Open phpMyAdmin at `http://localhost/phpmyadmin`.
3. Import **`database/schema.sql`**. This creates the `fhircare_db` database, all
   tables, and demo seed data (see accounts below).
4. If your MySQL root user has a password, edit
   `src/main/resources/application.properties` and set `spring.datasource.password=...`.
5. From the project root:
   - `mvn spring-boot:run`
   - or import as a Maven project in IntelliJ / Eclipse / VS Code and run
     `FhirCareApplication.java`.
6. Open **`http://localhost:8080`**.

