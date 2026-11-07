# Audit et corrections — Néon Kaiju

Référence : `specifications.md`, notamment §6–9 et RULE-01 à RULE-45. Les règles du dépôt priment sur les règles commerciales : notamment, les 20 ★ donnent la victoire **en fin de tour**, à condition d’être encore vivant. Tokyo Bay, les dés jaunes et les jetons restent hors périmètre.

## Causes et corrections

| Problème | Cause dans le code initial | Correction |
| --- | --- | --- |
| Compteur 2/2 trompeur | Le premier lancer était déjà créé par le moteur ; seuls les deux suivants étaient affichés | Lancer effectué / 3, nombre restant et premier lancer automatique explicités |
| Blocage après le dernier lancer | `RESOUDRE` attendait plusieurs commandes manuelles | Le moteur enchaîne les résultats après StopRolling ou le troisième lancer |
| Dés difficiles à comprendre | Seul le cadenas indiquait la conservation | Chaque dé affiche « Gardé » ou « À relancer », avec description d’accessibilité ; la conservation est portée par `GameState` |
| Choix d’ordre à préserver (RULE-09) | L’ordre était exprimé par les boutons de résolution manuelle | Ordre par défaut Chiffres → Cellules → Soins → Griffes ; les 24 ordres restent sélectionnables avant la résolution automatique |
| Commandes disponibles pendant le tour des bots | Les actions légales ne distinguaient pas l’utilisateur du décideur | Le moteur expose le décideur courant ; l’interface et le dispatch refusent les commandes humaines pendant une décision de bot |
| Dialogue absent en portrait | Retour anticipé avant les superpositions | Dialogues de fuite et de Surcharge communs aux deux orientations |
| Premier joueur toujours humain | Pas de concours initial | Concours des griffes, avec relance des seuls ex æquo (RULE-05) |
| Parties trop similaires | Mélange linéaire simple de la graine et du curseur | Mélange SplitMix64 déterministe, utilisé pour les dés et les mélanges |
| Bots trop rigides | Toutes les Cellules et plusieurs chiffres étaient gardés systématiquement ; achat du plus cher | Priorités contextualisées : santé, position, brelan ciblé, ressources nécessaires, proximité de victoire, valeur et conséquences de l’achat |
| Dégâts de plasma sur des monstres non visés | La cible AUTRES était utilisée sans contexte de l’attaque | Les effets après blessure ciblent les victimes réellement blessées |
| Implant sans effet utile | Modification d’une face après que tous les symboles avaient été résolus | Déclenchement avant le comptage définitif des symboles |
| Mort par effets pouvant bloquer la partie | Certaines transitions ne vérifiaient pas les éliminations | Contrôles après effets de début de tour, après résolution, entrée et achat ; joueur actif mort sauté |
| Soins surestimés dans le journal | Le nombre de cœurs était affiché, même au plafond de santé | Affichage du soin réellement obtenu ; aucun soin par les dés dans Tokyo |
| Protection du premier dégât réutilisable | Une attaque entièrement annulée ne marquait aucun dégât reçu | Les dégâts annulés sont mémorisés pour consommer cette protection, puis remis à zéro au tour suivant |
| Sauvegarde fragile | Écriture directe et suppression du résultat de partie | Écriture atomique, état final conservé, navigation restaurée et choix en attente repris sans relancer les dés |
| Documentation de Tokyo erronée | Les règles affichées exigeaient une griffe pour entrer | Entrée obligatoire après résolution dès que Tokyo est libre, même sans griffe |

## Ajustements explicités

- À la demande de l’utilisateur, **aucune carte ne permet un quatrième lancer**. « Recalibrage optique » transforme désormais un dé 1 en griffe au premier lancer. Le catalogue et son texte ont été mis à jour ensemble.
- `AVANT_RESOLUTION` complète les déclencheurs de §8 pour exécuter l’implant avant le comptage des symboles. `APRES_RESOLUTION` reste utilisé pour les effets qui interviennent réellement après ce comptage.
- La même politique est utilisée pour les trois bots conformément à §11. Il n’y a pas de niveaux de difficulté ni de pouvoirs liés au monstre choisi.
- La version de l’état passe à 2. Les anciennes sauvegardes gardent leurs dés et décisions courants, avec le compteur ramené dans la limite des trois lancers. Les futurs tirages emploient le nouveau générateur ; un ancien historique textuel n’est pas convertible en rejeu exact. Les nouvelles parties stockent également les actions structurées pour le rejeu.
- Le portrait existant est conservé. Le paysage reste la présentation de référence ; aucune fonctionnalité d’orientation n’a été supprimée.

## Vérifications automatisées

Commandes :

```bash
./gradlew :core:engine:test :core:ai:test :app:assembleDebug :app:lintDebug
```

- Règles : initialisation, dés conservés et libérés, trois lancers, arrêt anticipé, interdiction d’un quatrième lancer, chiffres de 1 à 6 exemplaires, Méchanceté, interruption puis reprise des choix, soins et plafond, cibles des griffes, fuite ou maintien, entrée obligatoire, bonus de début de tour, achat/remplacement/renouvellement, effets de fin de tour, victoire et éliminations.
- Sauvegarde : aller-retour JSON au milieu d’une fuite et d’un choix de Surcharge, avec comparaison du résultat après reprise.
- Catalogue : 20 cartes et 10 tuiles ; test dynamique identifié par l’ID de chaque contenu avec attentes sur son comportement.
- Bots : 100 parties complètes, chaque action vérifiée dans `legalActions`, invariants des ressources/santé/Tokyo vérifiés après chaque action. L’humain est piloté par la même politique dans cette simulation afin de terminer les quatre tours de manière autonome. Un test vérifie aussi le rejeu déterministe.
- Les tests JVM ne constituent pas une preuve d’ergonomie, ni une mesure de couverture à 85 %. Aucun résultat de couverture ou de captures Paparazzi n’est revendiqué sans mesure.

## Ambiguïtés et limites du projet

- Les validations demandées à l’enseignant en §11 (tuiles, exclusion des dés jaunes et jetons) restent ouvertes.
- Le prix minimal après réduction n’est pas spécifié. Le plancher de 1 Cellule existant est conservé, sans inventer une gratuité.
- Les textes « chaque tour » de certaines tuiles sont plus larges que leur déclencheur. Le fonctionnement existant reste celui des effets du propriétaire au début/à la fin de **son** tour ; cette portée doit être confirmée si une activation aux tours adverses est souhaitée.
- La phrase explicative de §7.4 après RULE-21 est contradictoire sur le moment où entre l’attaquant. La machine à états, RULE-25 et la demande de l’utilisateur convergent : l’entrée a lieu pendant la phase 3 du tour courant.
- L’architecture reste organisée en six modules. Les fonctions futures mentionnées dans les spécifications (audio, statistiques détaillées, Hilt, polices embarquées, CI, captures Paparazzi et couverture chiffrée) ne sont pas présentées comme achevées par cet audit.

## Compléments du document intégral

Le fichier intégral `Prompt_Gemini_Neon_Kaiju.txt` a été lu après la première pièce jointe tronquée. Les sections supplémentaires ont conduit aux compléments suivants :

- Menu Pause, reprise, sauvegarde et retour à l’accueil ; le traitement automatique des bots est limité à la route de jeu. Le bouton Retour Android ouvre aussi la pause pendant le jeu ou le marché.
- Joueur devant décider et phase courante explicités, y compris lorsqu’un occupant doit répondre à l’attaque d’un autre joueur actif.
- Motifs des décisions des bots affichés à partir de l’action réellement choisie, sans consommation supplémentaire du générateur.
- Documentation [stratégie des bots](docs/strategie-bots.md), avec exemples contrôlés par `BotExamplesTest`, et [aléatoire/rejeu](docs/aleatoire-et-rejeu.md).
- Graine conservée dans les données techniques mais retirée de l’interface courante, conformément à la demande.
- Résumé détaillé ouvrable depuis la Tour ; le panneau principal montre un extrait pour tenir en paysage.

## Résultats des commandes exécutées

- **81 tests JVM réussis**, sans échec : 34 sur le contenu, 38 sur les règles et transitions, 3 sur l’aléatoire, 2 sur les exemples et variations des bots, 4 sur leur comportement, les parties complètes et le retour au joueur humain.
- **100 parties simulées terminées**, sur les graines 0 à 99 ; les quatre joueurs apparaissent parmi les gagnants, maximum observé 470 actions par partie.
- **360 000 tirages statistiques** : 120 000 pour chacune des graines 0, 42, 2026. Chi carré observé : respectivement 1,996 ; 2,4114 ; 3,9042, pour le seuil de détection choisi à 30.
- **APK debug construit** : `app/build/outputs/apk/debug/app-debug.apk`.
- **Android Lint réussi**, sans erreur, avec 35 avertissements (principalement versions de dépendances, cible Android et ressources anciennes/inutilisées). Le projet conserve son SDK 35 actuel ; les spécifications mentionnent 36. Aucune mise à niveau générale des dépendances n’a été ajoutée à ces corrections.
- `git diff --check` réussi.
- Le JDK du démon Gradle a été ramené de 25 à 21, conformément aux spécifications : Lint échouait sous le JDK 25 configuré initialement.

Les rapports générés se trouvent dans `core/engine/build/reports/tests/test/`, `core/ai/build/reports/tests/test/` et `app/build/reports/lint-results-debug.html`. Ils ne sont pas ajoutés aux sources.

## Fichiers modifiés par responsabilité

| Responsabilité | Fichiers |
| --- | --- |
| État sérialisable, curseur, compteur, décideur, historique | `core/model/src/main/kotlin/com/neonkaiju/kot/model/Models.kt` |
| Résolution, tours, Tokyo, éliminations | `core/engine/src/main/kotlin/com/neonkaiju/kot/engine/GameEngine.kt` |
| Effets, protections, catalogue | `core/content/src/main/kotlin/com/neonkaiju/kot/content/EffectInterpreter.kt`, `core/content/src/main/resources/catalog.json` |
| Stratégie et explications | `core/ai/src/main/kotlin/com/neonkaiju/kot/ai/BotPolicy.kt` |
| Navigation, persistance et suspension des bots | `app/src/main/kotlin/com/neonkaiju/kot/MainActivity.kt` |
| Dés, actions, résumé, jeu portrait/paysage | `feature/game/src/main/kotlin/com/neonkaiju/kot/feature/game/Screens.kt`, `GameScreenPortrait.kt`, `DiceTurnControls.kt`, `NeonTheme.kt` |
| Pause, joueur actif, phase | `feature/game/src/main/kotlin/com/neonkaiju/kot/feature/game/PauseScreen.kt` |
| Règles visibles | `feature/game/src/main/kotlin/com/neonkaiju/kot/feature/game/RulesAndSettingsScreens.kt` |
| Tests | `RulesAndCatalogTest.kt`, `ContentEffectsTest.kt`, `RandomnessTest.kt` dans `core/engine/src/test/` ; `BotPolicyTest.kt`, `BotExamplesTest.kt` dans `core/ai/src/test/` |
| Exécution des tests et JDK | `core/ai/build.gradle.kts`, `gradle/gradle-daemon-jvm.properties` |
| Documentation | `README.md`, `VALIDATION.md`, `docs/strategie-bots.md`, `docs/aleatoire-et-rejeu.md` |

## Contrôles sur l’émulateur Android 36

L’APK a été installé et démarré sur Pixel 9a. Captures réellement observées : [jeu portrait](docs/captures-emulateur/jeu-portrait.png), [jeu paysage](docs/captures-emulateur/jeu-paysage.png), [pause](docs/captures-emulateur/pause.png).

| Contrôle | Résultat effectivement observé |
| --- | --- |
| Lisibilité du joueur devant agir et de la phase | « À TON TOUR », phase de lancer et bouton Pause visibles |
| Compteur | « Lancer 2/3 — 1 lancer restant » |
| Conservation | Deux dés conservés, faces inchangées après le deuxième lancer |
| Modification de sélection | Dé 1 libéré, dé 3 conservé, dé 2 toujours conservé ; compteur inchangé, assertions sur l’état sauvegardé réussies |
| Rotation | État sérialisé strictement identique avant/après rotation ; assertion réussie |
| Commandes en paysage | Les boutons de relance et d’arrêt/résolution sont entièrement visibles, contrairement au débordement détecté lors de la première capture et corrigé |
| Pause | Écran avec Reprendre, Sauvegarder et quitter, Retour à l’accueil observé ; retour au jeu observé |
| Reprise après fermeture et troisième lancer dans le parcours interactif final | Non terminés : l’émulateur s’est arrêté et ADB a renvoyé `no devices/emulators found` au démarrage du dernier scénario. Ils ne sont pas déclarés réussis sur appareil. Les tests JVM du compteur, de la résolution automatique et des états sérialisés sont réussis. |
| Suspension pendant un tour de bot et tour complet humain/bots sur appareil | Pas de validation interactive complète revendiquée ; le contrôleur n’exécute les bots que sur la route GAME, et la simulation JVM vérifie le retour au joueur humain |

L’environnement a également présenté un dialogue « System UI isn’t responding », puis un scénario ADB long a été terminé avec le code 143. Les contrôles ont été fractionnés pour valider la sélection et la rotation. Il reste à rejouer le parcours interactif complet sur un appareil ou un émulateur stable. Ces captures ne sont pas des tests Paparazzi ; Android 10/API 29 et un appareil physique n’ont pas été vérifiés dans cette session.

## Correction de visibilité des gains — 26 octobre 2026

Le retour utilisateur « les gains ne se voient pas lorsqu’on teste » a mis en évidence une limite de l’affichage : le journal affiché était tronqué aux trois dernières lignes, puis recentré sur le tour suivant. Les tests du calcul ne validaient pas cette lisibilité.

Le moteur conserve désormais, pour chaque joueur, les gains réellement attribués lors de sa dernière résolution. Ce bilan est sérialisé, reste visible pendant les tours adverses et cumule correctement les opérations séparées par une décision de fuite ou de Surcharge. Les bonus de début de tour sont identifiés séparément. Les gains affichés respectent les plafonds réels : par exemple, passer de 19 à 20 points affiche +1, même si le brelan avait une valeur théorique supérieure.

La fiche du joueur affiche directement les points, Cellules, PV récupérés et Surcharge déjà ajoutés. Les fiches adverses montrent également leurs derniers gains. Aucun menu ni ouverture du journal n’est nécessaire pour voir ce bilan. Le calcul des récompenses n’est pas modifié.

`VisibleGainsTest` couvre les gains cumulés avec l’entrée dans Tokyo, leur persistance pendant les tours des bots, la sérialisation, la reprise après choix de Surcharge et de fuite sans double comptabilisation, les plafonds, les résultats sans gain et les bonus de début de tour.
