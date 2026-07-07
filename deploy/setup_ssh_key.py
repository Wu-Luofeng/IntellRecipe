import paramiko
import os
import sys

host = '129.204.203.184'
user = 'ubuntu'
password = 'Wlf18620248370'

# 读取本地公钥
key_path = os.path.join(os.environ['USERPROFILE'], '.ssh', 'id_rsa.pub')
with open(key_path, 'r') as f:
    pubkey = f.read().strip()

# 连接服务器
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(host, username=user, password=password, timeout=10)

# 上传公钥
commands = [
    'mkdir -p ~/.ssh && chmod 700 ~/.ssh',
    'echo "{}" >> ~/.ssh/authorized_keys'.format(pubkey),
    'chmod 600 ~/.ssh/authorized_keys',
    'sort -u ~/.ssh/authorized_keys -o ~/.ssh/authorized_keys',
]
for cmd in commands:
    stdin, stdout, stderr = ssh.exec_command(cmd)
    err = stderr.read().decode()
    if err:
        print('CMD ERR: ' + err)

# 验证
stdin, stdout, stderr = ssh.exec_command('wc -l ~/.ssh/authorized_keys')
print('authorized_keys lines: ' + stdout.read().decode().strip())

ssh.close()
print('SSH key deployed successfully')