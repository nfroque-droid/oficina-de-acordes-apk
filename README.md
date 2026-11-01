# Oficina de Acordes · APK Android

A Oficina de Acordes (Strum Forge) empacotada como app Android. A app é a mesma página HTML, aberta num
WebView, e funciona **sem internet**: as fontes e as bibliotecas de PDF vão dentro da APK.

## Compilar no GitHub

1. Cria um repositório novo no GitHub (pode ser privado) e envia para lá esta pasta inteira.
   A pasta `.github` também tem de ir.
2. Abre o separador **Actions**. A compilação "Compilar APK" arranca sozinha a cada push para `main`.
   Também a podes lançar à mão em *Run workflow*.
3. Quando terminar (uns 3 a 5 minutos), abre a execução. Em **Artifacts**, descarrega `OficinaDeAcordes-apk`.
   É um .zip com a APK dentro.
4. No telemóvel, abre a APK e aceita "instalar de fontes desconhecidas" para o browser ou a app de ficheiros.

Para ter a APK na página **Releases**, cria uma tag que comece por `v`:

```
git tag v1.0
git push origin v1.0
```

## Atualizar a app

Substitui `web/oficina.html` pela versão nova da página e faz push. O workflow corre
`tools/prepare_web.py`, que:

- troca o Google Fonts e as bibliotecas da CDN pelas cópias locais;
- liga a ponte Android.

O código da app não é alterado. Cada APK nova instala por cima da anterior e mantém os favoritos, as definições e o registo de prática.

## O que muda em relação à versão no claude.ai

| Função | Na APK |
| --- | --- |
| Guardar cópia, exportar PDF | Guarda em *Transferências/Oficina de Acordes* e abre a folha de partilha (OneDrive, Drive, e-mail…). |
| Página para imprimir | Abre o diálogo de impressão do Android (inclui *Guardar como PDF*). |
| Restaurar cópia | Abre o seletor de ficheiros do telemóvel. |
| Tablaturas (Guitar Pro, MusicXML, texto) | Funcionam sem internet: o leitor alphaTab, a fonte de música e os sons vão dentro da APK. Os ficheiros .gp abrem-se pelo seletor de ficheiros do telemóvel. |
| Sites de cifras e ligações externas | Abrem no browser do telemóvel. |
| Sincronização dos favoritos com a conta | Não existe: tudo fica guardado no telemóvel. Para passar dados entre dispositivos, usa *Guardar cópia* e *Restaurar*. |
| "Perguntar à IA" na procura de músicas | O Claude não está disponível na APK. Usa Qwen, DeepSeek ou Kimi com a tua chave de API (em *Procurar músicas → Chaves e modelos*). Na APK os pedidos saem pelo Android, sem os bloqueios de CORS do browser. |

## Assinatura

Sem configurar nada, a APK é assinada com a chave do projeto (`keystore/oficina.jks`, palavra-passe `oficina-acordes`).
Assim, todas as APKs saem com a mesma assinatura e atualizam por cima umas das outras.
Serve para uso pessoal. Se um dia quiseres publicar na Play Store, cria a tua chave e guarda-a nos segredos
do repositório (*Settings → Secrets and variables → Actions*):

| Segredo | Valor |
| --- | --- |
| `KEYSTORE_BASE64` | o ficheiro .jks em base64 (`base64 -w0 minha.jks`) |
| `KEYSTORE_PASSWORD` | palavra-passe do keystore |
| `KEY_ALIAS` | alias da chave |
| `KEY_PASSWORD` | palavra-passe da chave |

Atenção: mudar de chave obriga a desinstalar a versão anterior. Antes disso, faz *Guardar cópia*.

## Estrutura

```
web/oficina.html                 página original (a que se atualiza)
app/src/main/assets/www/libs/alphatab/   leitor de tablaturas alphaTab 1.8.4 (MPL-2.0), fonte Bravura (OFL) e soundfont Sonivox
tools/prepare_web.py             gera app/src/main/assets/www/index.html
app/src/main/assets/www/         página pronta + fonts.css, fonts/, libs/, android-bridge.js
app/src/main/java/.../MainActivity.java   WebView, transferências, impressão, seletor de ficheiros
.github/workflows/apk.yml        compilação no GitHub Actions
```

Também abre no Android Studio (*Open* → esta pasta).

Requisitos: Android 8.0 ou mais recente.
