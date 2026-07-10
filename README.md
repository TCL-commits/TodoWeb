# Think-Calc-Loop

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
