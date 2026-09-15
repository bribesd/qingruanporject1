@echo off
start "backend" cmd /k "cd /d E:\CCCode\java-backend && mvn spring-boot:run"
start "frontend" cmd /k "cd /d E:\CCCode\frontend && npm run dev"
