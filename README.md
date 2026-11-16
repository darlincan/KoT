# Néon Kaiju

Jeu Android en Kotlin/Compose : un joueur humain affronte trois bots, avec un moteur de règles JVM indépendant d’Android. Les variantes du projet sont définies dans [specifications.md](specifications.md).

Prérequis : SDK Android 35 installé, et `sdk.dir` dans `local.properties` (Android Studio le crée) ou la variable `ANDROID_HOME`. Le JDK 21 est téléchargé automatiquement par Gradle s'il n'est pas installé.

```bash
./gradlew test :app:assembleDebug   # tous les tests JVM de tous les modules, puis l'APK
./gradlew :app:lintDebug            # analyse Android
```

APK de développement : `app/build/outputs/apk/debug/app-debug.apk`.

- [Audit, corrections et validation](VALIDATION.md)
- [Nouvelle analyse du marché, des ressources et de l’architecture](docs/reanalyse-marche-et-tours.md)
- [Stratégie réelle des bots et exemples vérifiés](docs/strategie-bots.md)
- [Graines, contrôles statistiques et rejeu](docs/aleatoire-et-rejeu.md)

La partie est sauvegardée automatiquement après chaque action. Le bouton Pause suspend les bots et permet de reprendre, de sauvegarder et quitter ou de revenir à l’accueil. Les décisions de fuite et de Surcharge restent celles du joueur humain, sans délai imposé.
