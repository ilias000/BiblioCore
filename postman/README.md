# BiblioCore API - Postman Collection

## Files

| File | Description |
|------|-------------|
| `BiblioCore_API.postman_collection.json` | Complete API collection with all endpoints |
| `BiblioCore_Local.postman_environment.json` | Environment for local development (H2) |
| `BiblioCore_Docker.postman_environment.json` | Environment for Docker (PostgreSQL) |

## Setup

1. Open Postman
2. Click **Import** (or `Ctrl+O` / `Cmd+O`)
3. Select all three JSON files
4. Select environment: **BiblioCore - Local** or **BiblioCore - Docker**

## Authentication

### Member Flow
1. Run **Register Member** - token auto-saved to `{{accessToken}}`
2. Or run **Login** with existing credentials
3. All member endpoints use `{{accessToken}}` automatically

### Admin Flow
1. Run **Login as Admin** with:
   - Email: `admin@bibliocore.com`
   - Password: `Admin123!`
2. Token auto-saved to `{{adminToken}}`
3. All admin endpoints use `{{adminToken}}` automatically

## Collection Structure

```
BiblioCore API
├── Authentication
│   ├── Register Member
│   ├── Login
│   └── Login as Admin
├── Books - Public
│   ├── Search Books (with filters)
│   ├── Get Book by ID
│   └── Get Book by ISBN
├── Books - Admin
│   ├── Create Book (+ sample books)
│   ├── Update Book
│   └── Delete Book
├── Members
│   └── Get My Profile
├── Loans
│   ├── Borrow Book
│   ├── Return Book
│   ├── Get My Loans
│   ├── Get My Active Loans
│   └── Get Loan by ID
├── Waitlist
│   ├── Join Waitlist
│   ├── Cancel Waitlist Entry
│   └── Get My Waitlist Entries
├── Admin - Members
│   ├── List All Members
│   ├── Get Member by ID
│   ├── Update Member
│   ├── Suspend Member
│   └── Activate Member
├── Admin - Loans
│   ├── List All Loans
│   ├── List Overdue Loans
│   ├── Get Member's Loans
│   └── Run Overdue Detection
├── Admin - Waitlist
│   └── Get Book Waitlist
├── Admin - Audit
│   ├── Get Loan Audit Log
│   └── Get Member Audit Log
└── Health & Info
    ├── Health Check
    ├── OpenAPI Spec
    └── Swagger UI
```

## Sample Workflows

### Borrow and Return a Book
1. **Login as Admin**
2. **Create Book** (Books - Admin)
3. **Register Member** or **Login**
4. **Borrow Book** (Loans)
5. **Get My Active Loans** - verify loan
6. **Return Book** - see fine if overdue

### Test Waitlist
1. **Create Book** with `totalCopies: 1`
2. **Borrow Book** as Member 1
3. **Join Waitlist** as Member 2
4. **Return Book** as Member 1 - Member 2 notified
5. **Borrow Book** as Member 2

### Admin Operations
1. **Login as Admin**
2. **List All Members**
3. **Suspend Member** - disable borrowing
4. **List Overdue Loans** - find late returns
5. **Get Loan Audit Log** - view history

## Environment Variables

| Variable | Description | Auto-set |
|----------|-------------|----------|
| `baseUrl` | API base URL | No |
| `accessToken` | Member JWT | Yes (login/register) |
| `adminToken` | Admin JWT | Yes (admin login) |
| `bookId` | Last retrieved book | Yes |
| `loanId` | Last created loan | Yes |
| `createdBookId` | Last created book | Yes |

## Troubleshooting

| Error | Solution |
|-------|----------|
| 401 Unauthorized | Token expired - login again |
| 403 Forbidden | Wrong role or member suspended |
| 404 Not Found | Resource doesn't exist |
| 409 Conflict | Business rule violation |
| Connection refused | Start the application first |