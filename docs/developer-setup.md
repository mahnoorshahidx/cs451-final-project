# Developer setup: running the project and pushing changes

A step-by-step guide for running Trip Budget Planner on your machine and sharing your changes with the team. Written for Windows with VS Code and PowerShell.

## 1. Install once

| Tool | Version | Check it's installed |
|---|---|---|
| Git | any recent | `git --version` |
| Java (JDK) | **21** | `java -version` |
| Node.js | 22 LTS (20.19+ also works; required by Vite 8) | `node -v` |
| MySQL Server + MySQL Workbench | 8 or newer | Win+R → `services.msc` → look for a `MySQL…` service |

You don't need to install Maven; the project includes it (`mvnw`).

## 2. Get the code

```powershell
git clone https://github.com/mahnoorshahidx/cs451-final-project.git
cd cs451-final-project
```

## 3. Allow npm in PowerShell (once)

Windows blocks npm in PowerShell by default (`npm.ps1 cannot be loaded because running scripts is disabled`). Run this once, type `Y`, then reopen the terminal:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

Or switch the VS Code terminal to **Command Prompt** (the ⌄ next to **+**), or type `npm.cmd` instead of `npm`.

## 4. Set up the database

1. Make sure MySQL is running: Win+R → `services.msc` → the `MySQL…` service should say **Running**. If not, right-click it and choose **Start**.
2. Open MySQL Workbench and connect with your MySQL user (usually `root`).
3. **File → Open SQL Script…** → `database/schema.sql` → click ⚡ to run the whole file. It creates the `trip_budget_planner` database and every table.
4. Optional sample data: run `database/seed.sql` the same way.

Every table in `schema.sql` uses `CREATE TABLE IF NOT EXISTS`, so re-running it is safe: tables you already have are skipped and missing ones are created.

### When a teammate changes the schema

Re-running `schema.sql` adds **new tables** but doesn't add **new columns to tables you already have**. If the backend then fails with `Schema validation: missing column [x] in table [y]`:

1. In Workbench, run `SHOW COLUMNS FROM y;`.
2. Compare with that table's `CREATE TABLE` in `schema.sql`.
3. Add each missing column with `ALTER TABLE`, copying its definition from `schema.sql`. For example:

```sql
USE trip_budget_planner;
ALTER TABLE users ADD COLUMN appearance VARCHAR(255) NOT NULL DEFAULT 'light';
```

Hibernate reports one missing column at a time, so check the whole table at once.

## 5. Tell the backend your MySQL password

The backend reads it from the `DB_PASSWORD` environment variable. **Never put it in `application.properties`**, because that file is committed.

Once, to save it for your Windows user:

```powershell
setx DB_PASSWORD "your-mysql-password"
```

Then **close and reopen VS Code completely**. Or set it for one terminal session only:

```powershell
$env:DB_PASSWORD="your-mysql-password"
```

If your MySQL username isn't `root`, also set `DB_USERNAME` the same way.

## 6. Run the app

Use two terminals side by side: open the terminal (Ctrl + \`), then click the split icon or press Ctrl+Shift+5.

**Terminal 1: backend**

```powershell
cd backend
.\mvnw spring-boot:run
```

It's ready when you see `Tomcat started on port 8080`. Leave it running.

**Terminal 2: frontend**

```powershell
cd frontend
npm install
npm run dev
```

`npm install` is only needed the first time and after someone changes `package.json`. Open the address it prints (usually http://localhost:5173), then sign up or log in.

Ctrl+C stops either one. Restart the backend after pulling backend changes; the frontend reloads on its own.

## 7. Common errors

| Error | Cause | Fix |
|---|---|---|
| `npm.ps1 cannot be loaded because running scripts is disabled` | PowerShell script policy | Step 3 |
| `Unable to determine Dialect without JDBC metadata` | Backend can't connect to MySQL | MySQL not running, or `DB_PASSWORD` not set in this terminal (steps 4–5) |
| `Access denied for user 'root'@'localhost'` | Wrong password | Check `DB_PASSWORD` |
| `Unknown database 'trip_budget_planner'` | Database not created | Run `schema.sql` (step 4) |
| `Schema validation: missing table [x]` | Your database is older than the code | Re-run `schema.sql` |
| `Schema validation: missing column [x] in table [y]` | A table changed after you created it | `ALTER TABLE` (step 4) |
| `Port 8080 was already in use` | Another backend is still running | Close the other terminal, or `netstat -ano \| findstr :8080` and end that process |
| Blank white page in the browser | A JavaScript error | F12 → Console. Often a missing export in `src/api/api.js` or a wrong import path |
| "Can't reach the server. Is the backend running?" | Backend stopped or crashed | Check terminal 1 |
| Test compile error: `constructor X cannot be applied to given types` | A constructor changed but a test wasn't updated | Update the test in `backend/src/test/...` to pass the new arguments (usually `mock(SomeRepository.class)`) |

## 8. Sharing your changes

### Before you start working

```powershell
git pull --rebase origin main
```

### When you're done

1. **See what changed**

   ```powershell
   git status
   ```

2. **Add only the files you meant to change.** Avoid `git add .`, which picks up stray files.

   ```powershell
   git add frontend/src/pages/MyPage.jsx frontend/src/styles/MyPage.css
   ```

3. **Commit with a short, specific message.** Split unrelated changes into separate commits.

   ```powershell
   git commit -m "Add savings history to Budget page"
   ```

   Add a second `-m "..."` for a one-line explanation if it helps.

4. **Get your teammates' latest changes on top of yours**

   ```powershell
   git pull --rebase origin main
   ```

5. **Push**

   ```powershell
   git push origin main
   ```

### Never commit

- Passwords, API keys, or `.env` files
- `node_modules/`, `target/`, `.vscode/`, `.vs/`, `.history/`
- Scratch files or setup notes that aren't meant for the team

### Merge conflicts

If `git pull --rebase` prints `CONFLICT`:

1. Open each listed file. VS Code shows **Accept Current / Accept Incoming / Accept Both** above each conflict. During a rebase, **Current** is the teammate's version and **Incoming** is yours. For files like `App.jsx`, **Accept Both** and then delete duplicate lines is usually right.
2. After fixing each file:

   ```powershell
   git add <file>
   git rebase --continue
   ```

3. Lost? `git rebase --abort` puts everything back exactly as it was before the pull.

### Undo mistakes

| Situation | Command |
|---|---|
| Unstage a file (keep your edits) | `git restore --staged <file>` |
| Undo your last N commits, keeping the changes (**only if not pushed yet**) | `git reset --soft HEAD~N` |
| Throw away your edits to one file | `git restore <file>` (can't be undone) |

Never rewrite commits that are already pushed. Ask the team first.

### After pushing

- Tell the team in your chat if you changed something they depend on: `schema.sql`, `api.js`, `App.jsx`, `Sidebar.jsx`, or an endpoint.
- Add the work to Jira and link the commit.