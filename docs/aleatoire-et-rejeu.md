# Dés, graines et rejeu

Le jeu utilise six faces équiprobables : `ONE`, `TWO`, `THREE`, `ENERGY`, `CLAW`, `HEART`. Une face correspond donc à une probabilité de 1/6. La graine est une donnée technique, sans effet sur les règles ; elle n’est pas affichée dans l’interface courante.

`DeterministicRng` est un générateur immuable fondé sur un mélange SplitMix64 de la graine et du curseur. Chaque dé non conservé consomme une valeur. Un changement de conservation n’en consomme aucune. Mélanger une liste de n éléments en consomme n − 1. Le mélange du marché, la sélection des monstres et le concours initial des griffes consomment également le générateur : le premier lancer du tour ne commence donc pas au curseur 0 d’une nouvelle partie.

En production, `MainActivity` fournit `System.currentTimeMillis()` à la création d’une partie. Aucune graine fixe n’est imposée aux joueurs. Deux graines différentes donnent généralement des séquences différentes ; elles peuvent toutefois produire certains résultats identiques.

## Reproduire un scénario JVM

```kotlin
val catalog = CatalogLoader.loadCatalog()
var state = GameEngine.createGame(
    seed = 42L,
    humanMonster = MonsterId.VOLTRAX,
    catalog = catalog
)
state = GameEngine.reduce(state, GameAction.ToggleKeep(0))
state = GameEngine.reduce(state, GameAction.Reroll)
```

La même graine, le même monstre humain, le même catalogue, la même version du moteur et la même suite d’actions produisent le même état. Le premier joueur peut être un bot : ce concours fait partie de l’initialisation reproductible.

Pour tester isolément un lancer ou un effet, construire un `GameState` avec un `DeterministicRng(seed, cursor)` et des dés explicites est autorisé. Ce sont des fixtures de test ; le moteur de production ne contient aucune séquence prédéterminée destinée à les satisfaire.

## Rejouer une partie enregistrée

Les nouvelles sauvegardes contiennent la graine et le curseur dans `rng`, ainsi que les actions structurées dans `actionHistory`. Pour une partie créée avec la version actuelle :

```kotlin
val saved: GameState = /* décodage JSON de la sauvegarde */
var replay = GameEngine.createGame(saved.rng.seed, saved.players.first { !it.isBot }.monster, catalog)
for (action in saved.actionHistory) {
    replay = GameEngine.reduce(replay, action)
}
check(replay == saved)
```

Ne pas recréer le générateur depuis la graine à chaque relance : reprendre le curseur sauvegardé. Pour poursuivre un état déjà sauvegardé, décoder l’état entier puis appeler le moteur avec l’action suivante.

Les sauvegardes de l’ancienne version gardent leur état courant lors de la migration, mais n’ont pas d’historique d’actions structuré et employaient un autre algorithme. Leur rejeu exact depuis la graine d’origine n’est donc pas garanti. Leurs prochains lancers utilisent l’algorithme actuel.

## Vérifications

`RandomnessTest` vérifie : séquences de 120 dés sur dix graines, présence des six faces, reproductibilité, variation entre graines, reprise au curseur enregistré et permutation lors d’un mélange. Un test statistique porte sur 120 000 tirages pour chacune des graines 0, 42 et 2026. Il calcule le chi carré contre six catégories équiprobables et exige un résultat inférieur à 30. C’est un détecteur d’anomalies volontairement large et déterministe, pas une preuve mathématique d’absence de biais.

`BotPolicyTest` joue 100 parties et vérifie le rejeu des actions. `RulesAndCatalogTest` vérifie qu’un dé conservé ne consomme pas de tirage et conserve sa face pendant une relance.

```bash
./gradlew :core:engine:test :core:ai:test
```
