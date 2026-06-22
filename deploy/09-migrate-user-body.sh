#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "===== 1. 重建 user-service ====="
cd "${PROJECT_ROOT}"
cd user-service
mvn clean package -DskipTests -q
echo "  user-service 构建完成"

echo ""
echo "===== 2. 数据库迁移：user 表增加身体字段 ====="
MYSQL_CNF="${SCRIPT_DIR}/my.cnf"
if [ -f "${MYSQL_CNF}" ]; then
  MYSQL_CMD="mysql --defaults-extra-file=${MYSQL_CNF} intell_recipe"
elif command -v mysql &>/dev/null; then
  MYSQL_CMD="mysql -u root -p intell_recipe"
else
  MYSQL_CMD="docker exec -i intellrecipe-mysql mysql -u root -p intell_recipe"
fi

SQL="ALTER TABLE \`user\`
  ADD COLUMN IF NOT EXISTS \`height\` decimal(5,1) DEFAULT NULL COMMENT '身高(cm)',
  ADD COLUMN IF NOT EXISTS \`weight\` decimal(5,1) DEFAULT NULL COMMENT '体重(kg)',
  ADD COLUMN IF NOT EXISTS \`age\` int DEFAULT NULL COMMENT '年龄',
  ADD COLUMN IF NOT EXISTS \`gender\` tinyint DEFAULT NULL COMMENT '性别 0:女 1:男';"

echo "  执行 SQL..."
if echo "${SQL}" | ${MYSQL_CMD}; then
  echo "  数据库迁移完成"
else
  echo "  MySQL 未运行或连接失败，请手动执行以下 SQL："
  echo ""
  echo "  ALTER TABLE \`user\`"
  echo "    ADD COLUMN \`height\` decimal(5,1) DEFAULT NULL COMMENT '身高(cm)',"
  echo "    ADD COLUMN \`weight\` decimal(5,1) DEFAULT NULL COMMENT '体重(kg)',"
  echo "    ADD COLUMN \`age\` int DEFAULT NULL COMMENT '年龄',"
  echo "    ADD COLUMN \`gender\` tinyint DEFAULT NULL COMMENT '性别 0:女 1:男';"
fi

echo ""
echo "===== 3. 重启服务 ====="
cd "${PROJECT_ROOT}"
bash "${SCRIPT_DIR}/05-stop-services.sh" 2>/dev/null || true
sleep 1
bash "${SCRIPT_DIR}/04-start-services.sh"

echo ""
echo "===== 完成！====="
echo "可以在“我的 → 编辑资料”中填写性别/身高/体重/年龄"
