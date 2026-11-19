#!/bin/sh
# Roda uma vez, no servidor novo da Oracle (Ubuntu): instala o Docker e abre as portas 80 e 443.
# Uso (o publicar.ps1 manda este arquivo):  sh ~/preparar-servidor.sh
set -e
echo "-> Atualizando o sistema..."
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y
echo "-> Instalando o Docker..."
if ! command -v docker >/dev/null 2>&1; then curl -fsSL https://get.docker.com | sudo sh; fi
sudo usermod -aG docker "$USER"
echo "-> Abrindo as portas 80 e 443 no firewall do Ubuntu (a imagem da Oracle bloqueia tudo por padrão)..."
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent
for p in 80 443; do
  sudo iptables -C INPUT -p tcp --dport $p -m state --state NEW -j ACCEPT 2>/dev/null || \
  sudo iptables -I INPUT 6 -p tcp --dport $p -m state --state NEW -j ACCEPT
done
sudo netfilter-persistent save
mkdir -p ~/taskgo/backups
echo "-> Pronto. Saia e entre de novo no servidor para o Docker funcionar sem sudo."
