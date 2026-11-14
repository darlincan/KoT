# King of Tokyo — Néon Kaiju

Adaptation Android du jeu de plateau *King of Tokyo – Dark Edition*, sur le thème original **Néon Kaiju**
(cyberpunk synthwave). Une partie oppose 1 joueur humain à 3 bots, en orientation paysage.

Le détail du projet (règles, périmètre, architecture, contrat des écrans) se trouve dans
[`specifications.md`](specifications.md).

## Équipe

- Darlin Canis KAPATA NGANKAM
- Adrien DEU
- Vanelle Ivanna TCHOUKA
- Ninon Carmene TSAFACK NGUEMEGNE

La répartition des tâches se trouve dans [`docs/repartition-taches.csv`](docs/repartition-taches.csv).

## Prérequis

- JDK 21 (la toolchain Gradle le récupère si absent). Lancez aussi Gradle lui-même avec un JDK 21
  (`JAVA_HOME`) : Android Lint échoue quand Gradle tourne sur un JDK 25.
- Android SDK 35 ; l'application cible Android 10 et plus (`minSdk 29`)

## Commandes

```bash
./gradlew :app:assembleDebug        # APK de debug
./gradlew :app:installDebug         # installe sur l'appareil ou l'émulateur connecté
./gradlew test                      # tous les tests JVM, sans émulateur
./gradlew :core:engine:check        # tests du moteur + seuil de couverture
./gradlew check                     # tout, y compris Android Lint
```

Le rapport de couverture du moteur est généré dans `core/engine/build/reports/jacoco/test/html/index.html`.

## Modules

| Module | Rôle |
| --- | --- |
| `:app` | application, navigation, thème |
| `:core:model` | types immuables : `GameState`, `PlayerState`, `GameAction`, `GameJournal`… |
| `:core:engine` | moteur de règles, Kotlin/JVM pur, sans aucune dépendance Android |
| `:core:content` | catalogue JSON des cartes et tuiles, interpréteur d'effets, parseur de conditions |
| `:core:ai` | politiques de décision des bots |
| `:core:data` | dépôts : partie en cours, catalogue, réglages |
| `:feature:game` | écrans Compose et ViewModels |

## Moteur de règles

Le moteur s'utilise à travers trois fonctions de `GameEngine` :

- `createGame(seed, humanMonster, catalog)` prépare une partie à 4 joueurs ;
- `legalActions(state)` liste les actions permises dans l'état courant ;
- `reduce(state, action)` applique une action et renvoie le nouvel état. Une action absente de
  `legalActions` lève une `IllegalActionException` : les bots ne choisissent que parmi les actions
  légales et ne peuvent donc pas tricher.

### Déterminisme et rejeu

Tout le hasard passe par le générateur stocké dans `GameState` (graine et curseur). Chaque action
acceptée par `reduce` est ajoutée à `GameState.history`.

- `GameEngine.journalOf(state)` produit un `GameJournal` sérialisable : graine, monstre du joueur et
  liste des actions. Il est à joindre à tout rapport de bug.
- `GameEngine.replay(journal, catalog)` rejoue la partie et reconstruit exactement le même état.

## Cartes et tuiles

Les 20 cartes et les 10 tuiles Méchanceté sont des **données**, dans
[`core/content/src/main/resources/catalog.json`](core/content/src/main/resources/catalog.json).
Ajouter ou modifier une carte ne demande que d'éditer ce fichier (grammaire au §8 de la spécification).

Le catalogue est validé à chaque build, à trois niveaux :

1. [`catalog.schema.json`](core/content/src/main/resources/catalog.schema.json) (JSON Schema 2020-12)
   fixe la structure : nombre de cartes et de tuiles, champs, déclencheurs, cibles et opérations
   autorisés. Le test `CatalogSchemaTest` vérifie que le catalogue le respecte et que le schéma reste
   aligné sur les énumérations Kotlin.
2. `CatalogValidator` vérifie ce qu'un schéma ne peut pas exprimer : identifiants uniques, conditions
   syntaxiquement valides, déclencheurs imposés par certaines opérations. Le chargeur refuse un
   catalogue invalide.
3. Chaque carte et chaque tuile a un test nommé d'après son identifiant (`CardEffectsTest`).

## Tests

- Une règle du livret correspond à un test nommé `RULE_xx_…`, d'après la numérotation du §7 de la
  spécification.
- Les tests du moteur tournent en JVM, sans émulateur ni mock : le générateur aléatoire est déterministe.
- `./gradlew :core:engine:check` échoue si la couverture des lignes de `:core:engine` passe sous
  **85 %** (JaCoCo).

## Thème et wireframes

Les wireframes des 7 écrans, en paysage, sont dans
[`wireframes/theme1-neon-kaiju.html`](wireframes/theme1-neon-kaiju.html).
