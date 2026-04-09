# Think-Calc-Loop

Think-Calc-Loop is a Spring Boot + Thymeleaf task board application for managing workspaces, projects, tasks, members, notifications, profiles, and avatars.

## Features

- Workspace and project management
- Kanban-style task board
- Task members, comments, attachments, checklist, and due dates
- User profile page with avatar upload
- Automatic avatar fallback when a user has no uploaded image
- Notifications and activity tracking

## Requirements

- Java 21
- Maven 3.9+ or the included Maven wrapper
- SQL Server configured through `src/main/resources/application.properties`

## Run Locally

1. Configure the database connection in `src/main/resources/application.properties`.
2. Start the application:

```bash
./mvnw spring-boot:run
```

3. Open `http://localhost:8080` in your browser.

## Test

Run the test suite with:

```bash
./mvnw test
```

## Notes

- Login and branding assets are served from `src/main/resources/static/icons`.
- Uploaded avatars are stored under `uploads/avatars`.
- If a user has no avatar, the app renders generated initials instead.
