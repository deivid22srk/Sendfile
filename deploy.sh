#!/bin/bash

# Script para criar repositório no GitHub e enviar o código
set -e

REPO_NAME="SendFile"
GITHUB_TOKEN="ghp_LGCrxSqFg5igMeK9k193NOXMY6jNL28jlkw"
GITHUB_USER="deivid22srk"

echo "=== Criando repositório no GitHub ==="

# Criar repositório via API
curl -s -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO_NAME\",\"description\":\"Android file transfer app via WiFi\",\"private\":false,\"auto_init\":false}"

echo ""
echo "✓ Repositório criado com sucesso!"
echo ""
echo "=== Configurando Git e enviando código ==="

# Inicializar repositório local
cd "$(dirname "$0")"

if [ ! -d ".git" ]; then
    git init
    echo "✓ Git inicializado"
fi

# Configurar remoto
git remote add origin https://$GITHUB_USER:$GITHUB_TOKEN@github.com/$GITHUB_USER/$REPO_NAME.git 2>/dev/null || git remote set-url origin https://$GITHUB_USER:$GITHUB_TOKEN@github.com/$GITHUB_USER/$REPO_NAME.git
echo "✓ Remoto configurado"

# Adicionar todos os arquivos
git add .
echo "✓ Arquivos adicionados"

# Commit
git commit -m "Initial commit: SendFile Android app

- File transfer via WiFi socket (fast, no Bluetooth)
- QR Code for device pairing
- Send/receive multiple files
- Real-time speed indicator
- GitHub Actions CI/CD" || echo "Commit já existe"

# Push para main
git branch -M main
git push -u origin main --force

echo ""
echo "✅ Código enviado para: https://github.com/$GITHUB_USER/$REPO_NAME"
