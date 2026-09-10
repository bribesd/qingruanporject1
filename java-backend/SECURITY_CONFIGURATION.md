# Secure deployment configuration

Set these environment variables before starting the service:

```powershell
$env:JWT_SECRET = "a-random-secret-that-is-at-least-32-bytes-long"
$env:DB_USERNAME = "enterprise_kb_app"
$env:DB_PASSWORD = "a-strong-database-password"
$env:DB_URL = "jdbc:mysql://localhost:3306/enterprise_kb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true"
```

Schema creation is disabled by default. Apply schema migrations with a deployment account, then run the application with a least-privileged database account.

To create the initial super administrator on an empty database, enable bootstrap for one run only:

```powershell
$env:APP_BOOTSTRAP_ENABLED = "true"
$env:APP_INITIAL_ADMIN_USERNAME = "admin"
$env:APP_INITIAL_ADMIN_PASSWORD = "a-unique-strong-password"
```

After the account is created, unset `APP_BOOTSTRAP_ENABLED` and `APP_INITIAL_ADMIN_PASSWORD`.
