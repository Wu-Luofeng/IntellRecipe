@echo off
echo === 0. Commit changes ===
cd /d e:\study\javaCode\springboot\IntellRecipe
git add -A
git commit -m "feat: add voucher validity period with lazy expiration"

echo === 1. Push code to server ===
git push origin main

echo === 2. Run database migration ===
echo Wlf18620248370 | ssh -o StrictHostKeyChecking=no ubuntu@129.204.203.184 "docker exec intellrecipe-mysql mysql -uroot -proot intell_recipe -e \"ALTER TABLE voucher ADD COLUMN validity_days int DEFAULT NULL COMMENT 'validity days' AFTER status; ALTER TABLE voucher_order ADD COLUMN expire_time datetime DEFAULT NULL COMMENT 'expire time' AFTER create_time; ALTER TABLE voucher_order ADD INDEX idx_expire_time (expire_time);\" 2>&1 || echo 'Migration may already be applied'"

echo === 3. Pull code and rebuild on server ===
echo Wlf18620248370 | ssh -o StrictHostKeyChecking=no ubuntu@129.204.203.184 "cd ~/IntellRecipe && git pull origin main && cd intellrecipe-common && mvn clean install -DskipTests -q && cd ../voucher-service && mvn clean package -DskipTests -q 2>&1 && echo 'BUILD SUCCESS'"

echo === 4. Restart voucher-service ===
echo Wlf18620248370 | ssh -o StrictHostKeyChecking=no ubuntu@129.204.203.184 "pkill -f 'voucher-service.*jar' || true && sleep 2 && cd ~/IntellRecipe && nohup java -Xms128m -Xmx256m -Duser.timezone=Asia/Shanghai -jar voucher-service/target/voucher-service-0.0.1-SNAPSHOT.jar > logs/voucher-service.log 2>&1 & echo PID=$! && sleep 5 && ps aux | grep voucher-service | grep -v grep && echo '---' && tail -5 logs/voucher-service.log"