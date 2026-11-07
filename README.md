# Néon Kaiju

Jeu Android en Kotlin/Compose : un joueur humain affronte trois bots, avec un moteur de règles JVM indépendant d’Android. Les variantes du projet sont définies dans [specifications.md](specifications.md).

Prérequis : JDK 21, SDK Android 35 installé et `sdk.dir` configuré dans le fichier local `local.properties`.

```bash
./gradlew :core:engine:test :core:ai:test :app:assembleDebug :app:lintDebug
```

APK de développement : `app/build/outputs/apk/debug/app-debug.apk`.

- [Audit, corrections et validation](VALIDATION.md)
- [Nouvelle analyse du marché, des ressources et de l’architecture](docs/reanalyse-marche-et-tours.md)
- [Stratégie réelle des bots et exemples vérifiés](docs/strategie-bots.md)
- [Graines, contrôles statistiques et rejeu](docs/aleatoire-et-rejeu.md)

La partie est sauvegardée automatiquement après chaque action. Le bouton Pause suspend les bots et permet de reprendre, de sauvegarder et quitter ou de revenir à l’accueil. Les décisions de fuite et de Surcharge restent celles du joueur humain, sans délai imposé.
