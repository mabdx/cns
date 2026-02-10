# CNS Postman Collection Guide (JWT Enabled)

This guide explains how to use the updated Postman collection with JWT authentication and newly added features.

## 1. Setup Variables
The collection uses Postman variables for convenience. After importing, click on the **CNS - Notification Service API** collection and go to the **Variables** tab:
- `base_url`: Default is `http://localhost:8080`.
- `jwt_token`: Your authentication token (see Section 2).
- `app_api_key`: The API key of the app you want to send notifications for.

## 2. Getting your JWT Token
Since the backend uses Google OAuth2, you cannot log in directly via Postman. Follow these steps:
1. Open your browser and go to: `http://localhost:8080/oauth2/authorization/google`
2. Complete the login with your Google account.
3. You will be redirected to a URL like: `http://localhost:3000/login-success?token=eyJhbGci...`
4. Copy the entire `token` value from the URL.
5. In Postman, paste this value into the `jwt_token` variable.

> [!NOTE]
> All requests in the **Apps** and **Templates** folders automatically use this token.

## 3. Sending Notifications (API Key Auth)
Requests in the **Notifications** folder do **not** require a JWT token. They use the `apiKey` provided in the JSON body.
- Use the `app_api_key` variable to store your API key after registering an app.

## 4. Key Features & New Endpoints

### Application Lifecycle
- **Register App**: Create a new application (Requires JWT).
- **Archive/Unarchive**: Change application visibility.
- **Delete**: Soft-delete an application.

### Template Management
- **Paginated List**: `GET /api/templates` now supports `page`, `size`, `appId`, and `status`.
- **Activate/Archive**: Manage template lifecycle.
- **Partial Update**: Use `PATCH /api/templates/{id}` to update only specific fields.

### Advanced Notifications
- **Personalized Bulk**: `POST /api/notifications/send/bulk`
  ```json
  {
    "apiKey": "{{app_api_key}}",
    "templateId": 1,
    "recipientData": {
      "user1@example.com": { "name": "Alice", "otp": "1111" },
      "user2@example.com": { "name": "Bob", "otp": "2222" }
    }
  }
  ```
- **Retry**: `POST /api/notifications/{id}/retry` to re-attempt a failed notification.

## 5. Troubleshooting
- **401 Unauthorized**: Ensure your `jwt_token` is valid and not expired. The token typically expires after 24 hours.
- **404 Not Found**: Ensure the ID you are using (App ID, Template ID, or Notification ID) actually exists in the database.
- **400 Bad Request**: Check if the application or template is ARCHIVED or DELETED before performing actions.

---
**Happy Testing!** 🚀
