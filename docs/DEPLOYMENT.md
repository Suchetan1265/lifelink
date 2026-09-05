# Deploying LifeLink

The app ships as **one service**: the React build is packaged inside the Spring
Boot jar, so a single container serves the UI and the API from the same origin.
There is no CORS to configure and no second service to pay for.

Everything below is on a free tier.

| Piece | Service | Why this one |
|---|---|---|
| App | **Render** web service | Free HTTPS, deploys from a Dockerfile, `.onrender.com` subdomain |
| Database | **Neon** Postgres | Free tier that persists rather than expiring after a trial window |
| Cache and geo index | **Upstash** Redis | Free tier, speaks the Redis protocol over TLS |
| Message broker | **CloudAMQP** (Little Lemur) | Free RabbitMQ plan |
| Scheduler | — | Quartz runs in-process; nothing to provision |

Postgres is the only hard requirement. The app runs without Redis and RabbitMQ —
matching falls back to SQL, the rate limiter fails open, email is skipped — so
if a step below fails you still get a working deployment.

---

## 1. Database — Neon

1. Sign up at [neon.tech](https://neon.tech) and create a project.
2. Open **Connection Details** and copy the **JDBC** connection string. It looks
   like `jdbc:postgresql://ep-xxx.aws.neon.tech/neondb?sslmode=require`.
3. Note the username and password shown alongside it.

Flyway creates every table on first boot. There is nothing to run by hand.

## 2. Redis — Upstash

1. Sign up at [upstash.com](https://upstash.com) and create a Redis database.
2. From the details page copy the **endpoint host**, **port** and **password**.

Upstash requires TLS, which is why `SPRING_DATA_REDIS_SSL_ENABLED` is set below.

## 3. RabbitMQ — CloudAMQP

1. Sign up at [cloudamqp.com](https://cloudamqp.com) and create a **Little Lemur**
   instance.
2. Copy the **AMQP URL**. It looks like `amqps://user:pass@host/vhost`.

## 4. The app — Render

1. Sign up at [render.com](https://render.com) and connect your GitHub account.
2. **New → Web Service**, pick the `lifelink` repository.
3. Set **Runtime** to `Docker` — Render finds the `Dockerfile` at the repo root.
4. Choose the **Free** instance type.
5. Set **Health Check Path** to `/actuator/health/essential`.

   Not `/actuator/health`. The aggregate reports DOWN when Redis or RabbitMQ is
   unreachable, which would make Render restart an app that is serving fine. The
   `essential` group covers only the database.

6. Add the environment variables in the next section.
7. **Create Web Service**. The first build takes 5–10 minutes: it compiles the
   backend and runs an npm build inside the container.

### Environment variables

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `seed` — loads demo data so the deployment has something to show |
| `JWT_SECRET` | 32+ random bytes. **The app refuses to start without it**, deliberately |
| `ADMIN_EMAIL` | `admin@lifelink.local`, or your own |
| `ADMIN_PASSWORD` | Something strong; this account approves hospitals |
| `SPRING_DATASOURCE_URL` | The Neon JDBC string, including `?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | From Neon |
| `SPRING_DATASOURCE_PASSWORD` | From Neon |
| `SPRING_DATA_REDIS_HOST` | Upstash endpoint host |
| `SPRING_DATA_REDIS_PORT` | Upstash port |
| `SPRING_DATA_REDIS_PASSWORD` | Upstash password |
| `SPRING_DATA_REDIS_SSL_ENABLED` | `true` |
| `SPRING_RABBITMQ_ADDRESSES` | The CloudAMQP `amqps://…` URL |
| `WEB_BASE_URL` | Your Render URL, e.g. `https://lifelink.onrender.com`. Password reset links point here |
| `GOOGLE_CLIENT_ID` | Optional; omit and the Google button simply does not render |

Generate a JWT secret with:

```bash
openssl rand -base64 48
```

## 5. Google sign-in on the deployed URL

The OAuth client is tied to the origins you authorise, so the local one will not
work in production.

1. Open your client in the [Google Cloud console](https://console.cloud.google.com/apis/credentials).
2. Add your Render URL to **Authorised JavaScript origins**, e.g.
   `https://lifelink.onrender.com`. No trailing slash, `https` not `http`.
3. Keep `http://localhost:5173` there so local development keeps working.

Changes can take a few minutes to take effect.

---

## After the first deploy

Sign in as the admin address you configured. The seed profile creates a
hospital, a blood bank and twelve donors, all with the password `password123` —
fine for a public demo, and the reason to keep the admin password separate.

### The free tier sleeps

Render's free instances stop after about 15 minutes without traffic, and the
next request has to boot Spring again — expect **30–60 seconds** on the first
click. If you are sharing the link somewhere it matters, either mention it or
move to a paid instance, which removes the sleep.

### Redeploying

Render rebuilds on every push to `main`. Nothing else to do.

---

## Running the packaged build locally

Same artefact the container runs, useful for reproducing a deployment problem:

```bash
cd backend
./mvnw -Pwebapp clean package -DskipTests
SERVER_PORT=8082 java -jar -Dspring.profiles.active=dev target/lifelink-backend-0.0.1-SNAPSHOT.jar
```

The whole app is then on `http://localhost:8082` — UI and API on one port, as in
production. Plain `./mvnw package` skips the frontend build and stays fast for
backend work.
