# IPTV Armadillo

[English](#english) · [Português](#portugues)

<a id="english"></a>

## English

Android player for playlists you provide. Each entry becomes a channel. The app does not ship a channel list, ads, or third-party catalogs. Imported lists stay on the device.

### Features

- Import an M3U, M3U8, or PLS playlist from a URL or a local file. A text file with one URL per line also works.
- Optional User-Agent and Referer, for providers that require them.
- Search, categories, and channel logos.
- One channel at a time, with previous and next.
- Fullscreen playback with a side guide, so you can browse and switch channels while the video keeps playing.
- The screen stays awake while the player is open and can sleep again after you leave it.
- HLS, MPEG-TS, DASH, SmoothStreaming, and RTSP through Media3 ExoPlayer.

Lists are stored only in the app's private files and are excluded from Android backup and device transfer. A playlist is limited to 32 MB and 100,000 channels.

### Requirements

- Android Studio with a JDK 17 toolchain (the JBR bundled with Android Studio is enough)
- Android device or emulator, API 24 or newer
- A playlist you are allowed to play

### Build and run

Open the project in Android Studio and run the `app` configuration, or from the project root:

```bash
./gradlew :app:installDebug
```

On Windows:

```bat
gradlew.bat :app:installDebug
```

Unit tests for the playlist parser:

```bash
./gradlew :app:testDebugUnitTest
```

### What stays out of git

Do not commit playlist files. A `.m3u`, `.m3u8`, or `.pls` can contain stream addresses and access credentials. The root `.gitignore` ignores those extensions, along with build output, Android Studio files, `local.properties`, and signing keys.

<a id="portugues"></a>

## Português

Player de Android para listas que você mesmo fornece. Cada item vira um canal. O app não traz lista pronta, anúncio nem catálogo de terceiros. As listas importadas ficam no aparelho.

### Recursos

- Importa uma lista M3U, M3U8 ou PLS por URL ou por arquivo local. Um arquivo de texto com uma URL por linha também serve.
- User-Agent e Referer opcionais, para provedores que exigem esses cabeçalhos.
- Busca, categorias e logotipos dos canais.
- Um canal por vez, com anterior e próximo.
- Reprodução em tela cheia com guia lateral, para navegar e trocar de canal enquanto o vídeo continua.
- A tela permanece acesa com o player aberto e pode apagar de novo ao sair dele.
- HLS, MPEG-TS, DASH, SmoothStreaming e RTSP pelo Media3 ExoPlayer.

As listas ficam só nos arquivos privados do app e ficam de fora do backup e da transferência do Android. Uma lista é limitada a 32 MB e 100.000 canais.

### Requisitos

- Android Studio com JDK 17 (o JBR que vem com o Android Studio basta)
- Aparelho ou emulador Android, API 24 ou mais recente
- Uma lista que você tenha permissão para reproduzir

### Compilar e rodar

Abra o projeto no Android Studio e rode a configuração `app`, ou na raiz do projeto:

```bash
./gradlew :app:installDebug
```

No Windows:

```bat
gradlew.bat :app:installDebug
```

Testes de unidade do leitor de listas:

```bash
./gradlew :app:testDebugUnitTest
```

### O que fica fora do git

Não faça commit de arquivos de lista. Um `.m3u`, `.m3u8` ou `.pls` pode conter endereços de transmissão e credenciais. O `.gitignore` da raiz ignora essas extensões, além da saída de build, dos arquivos do Android Studio, de `local.properties` e das chaves de assinatura.
