// Chạy TỰ ĐỘNG bởi image mongo chính thức, CHỈ 1 LẦN lúc volume data còn rỗng
// (docker-entrypoint-initdb.d). Tạo 3 user, mỗi user chỉ có quyền readWrite trên đúng 1
// database của mình (Mongo tự tạo database ngay khi có user/collection đầu tiên trong đó —
// không cần lệnh "CREATE DATABASE" riêng như Postgres).

function createScopedUser(dbName, username, password) {
  const targetDb = db.getSiblingDB(dbName);
  const existing = targetDb.getUser(username);
  if (existing) {
    return;
  }
  targetDb.createUser({
    user: username,
    pwd: password,
    roles: [{ role: 'readWrite', db: dbName }],
  });
}

createScopedUser(
  process.env.MESSAGING_MONGO_DB_NAME,
  process.env.MESSAGING_MONGO_USER,
  process.env.MESSAGING_MONGO_PASSWORD
);
createScopedUser(
  process.env.MEDIA_MONGO_DB_NAME,
  process.env.MEDIA_MONGO_USER,
  process.env.MEDIA_MONGO_PASSWORD
);
createScopedUser(
  process.env.SOCIAL_MONGO_DB_NAME,
  process.env.SOCIAL_MONGO_USER,
  process.env.SOCIAL_MONGO_PASSWORD
);
