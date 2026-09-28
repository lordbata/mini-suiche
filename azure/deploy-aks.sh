#!/usr/bin/env bash
# ======================================================================
# Despliega mini-suiche en Microsoft Azure (AKS).
#
# Requisitos: Azure CLI (az), kubectl y una suscripción de Azure.
#   az login
#   bash azure/deploy-aks.sh          (desde la carpeta mini-suiche/)
#
# ⚠️ COSTO: crea recursos que se cobran por hora (AKS con 2 nodos ≈ 2-3 USD/día).
#    Al terminar de practicar, BORRA TODO con:
#      az group delete --name rg-mini-suiche --yes --no-wait
# ======================================================================
set -euo pipefail

LOCATION="${LOCATION:-eastus2}"
RG="rg-mini-suiche"
AKS="aks-mini-suiche"
SUFFIX="$(date +%s | tail -c 6)"
ACR="acrminisuiche${SUFFIX}"         # nombre global único, solo minúsculas y números
KV="kv-minisuiche-${SUFFIX}"         # nombre global único

echo "==> 1. Grupo de recursos (carpeta lógica que agrupa todo)"
az group create --name "$RG" --location "$LOCATION" -o none

echo "==> 2. Azure Container Registry (repositorio privado de imágenes Docker)"
az acr create --resource-group "$RG" --name "$ACR" --sku Basic -o none

echo "==> 3. Construir las imágenes EN AZURE (no necesitas Docker instalado)"
az acr build --registry "$ACR" --image card-vault-service:1.0.0 --build-arg SERVICE=card-vault-service .
az acr build --registry "$ACR" --image payment-service:1.0.0    --build-arg SERVICE=payment-service .

echo "==> 4. Clúster AKS (Kubernetes administrado por Azure)"
#   --attach-acr:            el clúster puede descargar imágenes del ACR sin contraseñas
#   --network-policy azure:  hace cumplir las NetworkPolicy (segmentación PCI)
#   azure-keyvault-secrets-provider: add-on que trae secretos desde Key Vault
az aks create \
  --resource-group "$RG" --name "$AKS" \
  --tier free --node-count 2 --node-vm-size Standard_B2s \
  --network-plugin azure --network-policy azure \
  --attach-acr "$ACR" \
  --enable-addons azure-keyvault-secrets-provider \
  --generate-ssh-keys -o none

echo "==> 5. Azure Key Vault (almacén de claves y secretos, con RBAC)"
az keyvault create --resource-group "$RG" --name "$KV" --location "$LOCATION" \
  --enable-rbac-authorization true -o none
KV_ID="$(az keyvault show --name "$KV" --query id -o tsv)"

# Tú (para poder escribir secretos)
ME="$(az ad signed-in-user show --query id -o tsv)"
az role assignment create --role "Key Vault Secrets Officer" --assignee "$ME" --scope "$KV_ID" -o none

# El add-on del clúster (solo LECTURA de secretos: mínimo privilegio)
KV_IDENTITY_CLIENT_ID="$(az aks show -g "$RG" -n "$AKS" --query addonProfiles.azureKeyvaultSecretsProvider.identity.clientId -o tsv)"
KV_IDENTITY_OBJECT_ID="$(az aks show -g "$RG" -n "$AKS" --query addonProfiles.azureKeyvaultSecretsProvider.identity.objectId -o tsv)"
az role assignment create --role "Key Vault Secrets User" \
  --assignee-object-id "$KV_IDENTITY_OBJECT_ID" --assignee-principal-type ServicePrincipal \
  --scope "$KV_ID" -o none

echo "    (esperando 30s a que se propaguen los permisos RBAC)"
sleep 30

echo "==> 6. Guardar secretos en Key Vault (claves generadas al azar, nunca en Git)"
az keyvault secret set --vault-name "$KV" --name vault-encryption-key --value "$(openssl rand -base64 32)" -o none
az keyvault secret set --vault-name "$KV" --name vault-hmac-key       --value "$(openssl rand -base64 32)" -o none
az keyvault secret set --vault-name "$KV" --name vault-db-password    --value "vault_pwd" -o none
az keyvault secret set --vault-name "$KV" --name payments-db-password --value "payments_pwd" -o none

echo "==> 7. Conectar kubectl al clúster"
az aks get-credentials --resource-group "$RG" --name "$AKS" --overwrite-existing

echo "==> 8. Oracle de práctica dentro del clúster (en producción: Oracle Database@Azure)"
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/local-oracle/oracle.yaml
kubectl -n mini-suiche rollout status statefulset/oracle --timeout=10m

echo "==> 9. Desplegar los microservicios"
OUT="azure/.out"
rm -rf "$OUT" && cp -r azure/k8s "$OUT"
TENANT_ID="$(az account show --query tenantId -o tsv)"
sed -i \
  -e "s/__ACR_NAME__/$ACR/g" \
  -e "s/__KEYVAULT_NAME__/$KV/g" \
  -e "s/__TENANT_ID__/$TENANT_ID/g" \
  -e "s/__KV_IDENTITY_CLIENT_ID__/$KV_IDENTITY_CLIENT_ID/g" \
  "$OUT"/*.yaml
kubectl apply -k "$OUT"
kubectl -n mini-suiche rollout status deployment/card-vault-service --timeout=5m
kubectl -n mini-suiche rollout status deployment/payment-service --timeout=5m

echo
echo "✅ Listo. Pods:"
kubectl -n mini-suiche get pods -o wide
echo
echo "Probar (en otra terminal):  kubectl -n mini-suiche port-forward svc/payment-service 8082:8082"
echo "BORRAR TODO al terminar:    az group delete --name $RG --yes --no-wait"
