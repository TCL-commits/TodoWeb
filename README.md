# Think-Calc-Loop

Web: [tcltodo.onrender.com](https://tcltodo.onrender.com)

Think-Calc-Loop is a Spring Boot + Thymeleaf task board application for managing workspaces, projects, tasks, members, notifications, profiles, and avatars.

## Features

- Landing, login, and register pages
- Dashboard with workspace overview, task stats, and recent notifications
- Workspace management, including create workspace and member management
- Project management with templates for kanban, sprint, and marketing flows
- Kanban-style task board with task filters and drag/move actions
- Task details with members, comments, attachments, checklist, due dates, notes, and completion toggle
- Task assignment by member email, including auto-adding a workspace member when needed
- Search across workspaces, projects, and tasks with advanced query filters
- User profile page with avatar upload and contact info updates
- Automatic avatar fallback when a user has no uploaded image
- Notifications, activity tracking, audit log export, and realtime updates
- Workspace export/import and task archive/recycle-bin handling
- Advanced planning features such as sprints, burndown, dependencies, custom fields, time tracking, approval, and quality reports

## Requirements

- Java 21
- Maven 3.9+ or the included Maven wrapper
- PostgreSQL database hosted on Neon or another PostgreSQL provider
- Render environment variables for `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`

## Run Locally

1. Configure the database connection in `src/main/resources/application.properties` or export the Neon connection variables in your shell.
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

## Deploy on Render

1. Create a Render Web Service from this repository.
2. Set the build command to `./mvnw clean package -DskipTests`.
3. Set the start command to `java -jar target/demo-0.0.1-SNAPSHOT.jar`.
4. Add the Neon PostgreSQL connection values as environment variables:
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
5. Keep `PORT` unset unless you want Render to inject a custom port; the app already defaults to `8080` when `PORT` is missing.

## Notes

- Login and branding assets are served from `src/main/resources/static/icons`.
- Uploaded avatars are stored under `uploads/avatars`.
- If a user has no avatar, the app renders generated initials instead.
- The app uses PostgreSQL/Hibernate schema auto-update, so make sure Neon is reachable before the first startup.
