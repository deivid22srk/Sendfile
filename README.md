# SendFile - Transferência de Arquivos Android

App Android para transferência de arquivos entre dispositivos Android na mesma rede WiFi local.

## Recursos

- ✅ Transferência rápida via Socket TCP (WiFi local)
- ✅ Sem dependência de Bluetooth
- ✅ Sem limitação de velocidade de internet
- ✅ QR Code para conexão entre dispositivos
- ✅ Indicador de velocidade em tempo real
- ✅ Envio múltiplos arquivos
- ✅ Seleção de pasta para recebimento
- ✅ Interface simples e intuitiva

## Como usar

### Receber arquivos:
1. Abra o app e clique em **"Receber Arquivos"**
2. O app irá gerar um **QR Code** com as informações de conexão
3. Selecione a pasta onde os arquivos serão salvos
4. Aguarde o envio do outro dispositivo

### Enviar arquivos:
1. Abra o app e clique em **"Enviar Arquivos"**
2. Selecione os arquivos que deseja enviar
3. Clique em **"Escanear QR Code"** e escaneie o QR Code do dispositivo receptor
4. A transferência será iniciada automaticamente

## Requisitos

- Android 8.0+ (API 26+)
- Ambos dispositivos devem estar na **mesma rede WiFi**
- Permissões de armazenamento e câmera

## Build

### Build local:
```bash
./gradlew assembleDebug
```

### Build Release:
```bash
./gradlew assembleRelease
```

## GitHub Actions

O projeto inclui um workflow de CI/CD que permite:

- **Build manual**: Acesse Actions > Build APK > Run workflow
- **Build automático**: Em pushes para main/master
- **Upload de APK**: O APK gerado está disponível em Artifacts

## Tecnologias

- **Kotlin** como linguagem principal
- **Socket TCP** para transferência de arquivos
- **ZXing** para geração/leitura de QR Codes
- **Coroutines** para operações assíncronas
- **Material Design 3** para interface

## Licença

MIT License
