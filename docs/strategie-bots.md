# Stratégie des trois bots

La classe `HeuristicBotPolicy`, dans `core/ai`, pilote les trois adversaires avec la même politique. Le monstre choisi ne donne aucun pouvoir intrinsèque. Il n’existe pas de difficulté cachée : les différences viennent des ressources, de la position, des dés et d’une préférence reproductible liée à la partie.

## 1. Validité des décisions

`GameEngine.legalActions(state)` est l’autorité pour les actions permises. `chooseAction` commence par cette liste ; les changements de conservation sont vérifiés dans celle-ci et les actions retournées doivent lui appartenir. Une action illégale est rejetée par le moteur. Une catégorie n’est jamais résolue deux fois.

Le décideur est le joueur actif, sauf pendant la décision de fuite, qui appartient à l’occupant blessé. Un choix de tuile de Surcharge interrompt les autres opérations avant leur reprise. Le contrôleur Android utilise `BotTurnPresenter` : 450 ms par conservation, 1 800 ms pour les actions importantes et 2 200 ms avant la fin du tour ; le mode rapide utilise 400 ms. Il attend indéfiniment une décision humaine et ne fait pas avancer les bots sur un écran de pause, d’accueil ou pendant que l’Activity est en arrière-plan.

L’entrée dans Tokyo est obligatoire si la Tour est libre après résolution : ce n’est pas un choix stratégique du bot. Elle ne dépend pas des griffes.

## 2. Critères de conservation des dés

La sélection est recalculée à partir du résultat courant. Le bot modifie un seul dé par action, puis relance lorsque sa sélection est prête. La sélection reste stable pendant ces changements : conserver un dé ne consomme aucune valeur du générateur.

| Face | Critère réel |
| --- | --- |
| Cœur | Hors de Tokyo, conserver des cœurs si au moins 3 PV manquent, au plus autant que les PV manquants. Dans Tokyo, aucun cœur n’est recherché pour se soigner. |
| Griffe | Conserver uniquement s’il existe une cible. Dans Tokyo, les griffes sont gardées. Hors de Tokyo, elles le sont si le bot a moins de 16 ★ ou possède au moins deux griffes. |
| Éclair | Conserver si l’énergie manque pour la carte la moins chère du marché, si moins de 6 PV manquent, et selon la préférence décrite ci-dessous. |
| Chiffres | Viser un seul chiffre, présent au moins deux fois, si moins de 6 PV manquent. Le bot ne disperse pas ses dés sur plusieurs paires. |

Pour choisir le chiffre ciblé, le score est `10 × nombre de dés de ce chiffre + bonus` :

- `1` : bonus 4 si la Surcharge vaut 1, 2, 4, 5, 8 ou 9 ; sinon 0.
- `2` : bonus 3 si elle vaut 2, 5 ou 9 ; sinon 1.
- `3` : bonus 2.

Le nombre de dés prime donc sur le bonus. Les petits chiffres deviennent intéressants près des paliers de Surcharge ; les 3 apportent davantage de points directs. Une santé très basse conduit à abandonner les chiffres et les éclairs pour chercher des soins, tout en conservant les griffes qui ont une cible.

Quand tous les dés sont conservés, le bot choisit `StopRolling`, même au premier lancer. Sinon, il utilise `Reroll` si cette action est disponible. Le troisième lancer lance automatiquement la résolution : le bot n’obtient jamais de quatrième lancer et ne commande pas manuellement chacune des catégories.

## 3. Part d’aléatoire et reproductibilité

Le bot emploie une préférence binaire calculée par `(graine XOR identifiant du bot XOR curseur du générateur) AND 1`.

- Préférence 0 : conserver les éclairs utiles même avec une paire de chiffres.
- Préférence 1 : privilégier la paire de chiffres ; conserver les éclairs utiles seulement sans paire du chiffre ciblé.

Ce départage intervient uniquement lorsque plusieurs objectifs sont plausibles. Les restrictions de santé, de position et de légalité restent prioritaires. L’évaluation d’une action et l’affichage de son explication ne consomment pas de tirage. Le même état produit toujours la même décision. Les identifiants et les curseurs varient entre les bots et les tours ; deux graines différentes peuvent produire la même décision, ce qui est normal.

Les égalités d’évaluation des achats ou des tuiles sont départagées par l’ordre stable des actions. Il n’y a pas de tirage à chaque recomposition, ni de prétention à une stratégie optimale.

## 4. Rester ou fuir Tokyo

Le décideur est l’occupant blessé. Il note avec `evaluate` deux projections de son prochain tour, sans appeler le moteur, pour ne pas lancer les dés du joueur suivant :

- rester : +2 ★ et une perte moyenne de griffes qui dépend des adversaires vivants (1 ♥ face à un, 3 ♥ face à deux, 4 ♥ face à trois) ;
- fuir : perte de 1 ♥, et l’attaquant occupe la Tour.

Il ne reste que si cette projection est strictement meilleure. À 18 ★ et avec assez de ♥ pour survivre, rester mène à 20 ★ et l’emporte sur tout le reste ; à 2 ♥ face à trois adversaires, il fuit. Les dégâts d’une carte n’ouvrent pas de choix de fuite.

## 5. Achats et tuiles

Le bot simule chaque candidat sur un état immuable, puis garde la meilleure note `evaluate` (`core/ai/.../Evaluation.kt`). Cela ne modifie ni la partie réelle ni son curseur aléatoire. Pour les achats, chaque candidat est joué jusqu’à la fin du tour et comparé à `EndTurn`, afin que tous subissent les mêmes effets de fin de tour. Cette version n’utilise pas volontairement le renouvellement du marché : le joueur humain conserve cette possibilité.

La note est une fonction pure qui ne lit que l’information publique, jamais les dés, la pioche ou le marché :

| Élément | Valeur |
| --- | --- |
| Élimination, défaite en fin de partie | note minimale |
| Victoire, ou 20 ★ en étant vivant | note maximale |
| ★ | 10 par ★, plus 3 × (★ − 14)² au-delà de 14 |
| ♥ | 4 par ♥, moins 25 par ♥ manquant sous 4 |
| Cellule | 2 |
| Carte POUVOIR | 2,5 × son prix |
| Tuile | 12, plus la valeur de ses effets |
| Tour centrale | 15 − 30 × adversaires vivants / ♥ |
| Adversaire | −1 par ♥, et −2 × (★ − 15)² au-delà de 15 |

Dans une tuile, chaque unité d’effet vaut 10 pour les ★, 5 pour une annulation de dégât, 4 pour un soin, 3 pour un dégât, 2 pour une Cellule ou une réduction de coût, et 1 pour les autres effets. Il s’agit d’une estimation simple : les déclencheurs futurs et toutes les conditions ne sont pas projetés dans le futur.

## 6. Exemples vérifiables

Les scénarios ci-dessous sont des états de test explicites. Ils ne correspondent pas à un résultat forcé dans une nouvelle partie en production. Ils sont exécutés dans `BotExamplesTest` ; le journal du test contient chaque conservation, relance, résultat final et motif affiché.

### A. Chercher des soins hors de Tokyo

État : bot 1, 3 PV, 0 Cellule, 0 ★, Tour occupée par le joueur 0, graine 0 et curseur 0. Dés initiaux : `♥ ♥ griffe éclair 3 3`.

Il conserve les deux cœurs et la griffe. Les 7 PV manquants l’amènent à libérer les chiffres et l’éclair. Le deuxième lancer donne `♥ ♥ griffe éclair 1 éclair`. Il garde la même sélection et relance encore les trois dés libres. Le résultat final est `♥ ♥ griffe 3 éclair éclair`. La résolution lui donne 2 PV (santé finale 5), 2 Cellules et inflige 1 dégât à l’occupant. Le moteur attend ensuite la décision de cet occupant. Les soins s’appliquent avant les griffes avec l’ordre par défaut.

### B. Arrêter avec tous les dés conservés

État : bot 1, 10 PV, 0 Cellule, Tour occupée par le joueur 0, graine 1 et curseur 0. Dés : `éclair éclair éclair éclair éclair éclair`.

La préférence vaut 0 et le marché propose des cartes payantes. Le bot conserve les six éclairs, un par un, puis termine ses lancers. Il n’effectue aucune relance : la résolution au lancer 1/3 rapporte 6 Cellules. Il entre ensuite dans la phase d’achat et évalue les cartes disponibles.

### C. Ne pas chercher de soins dans Tokyo

État : bot 1 dans Tokyo, 3 PV, 0 Cellule, 0 ★, graine 0 et curseur 0. Dés : `♥ ♥ ♥ ♥ ♥ ♥`.

Il ne conserve aucun cœur et relance les six dés. Le deuxième lancer donne `éclair 1 éclair 3 éclair éclair`. Il conserve les quatre éclairs et relance les deux chiffres. Le dernier résultat est `éclair 3 éclair griffe éclair éclair`. Il gagne 4 Cellules, inflige 1 dégât à chacun des trois monstres hors de Tokyo et reste à 3 PV. Un éventuel cœur final ne pourrait pas le soigner. Son choix de fuite ne sera proposé que lors d’une attaque ultérieure qui le blesse.

### D. Deux objectifs valables dans une situation comparable

Bot 1, 10 PV, 0 Cellule, Tour occupée par le joueur 0, curseur 0, dés `éclair 3 3 cœur 1 2`. Avec la graine 0, la préférence vaut 1 : le bot commence par conserver le premier 3. Avec la graine 1, elle vaut 0 : il commence par conserver l’éclair. Les deux décisions sont légales et reproductibles ; un test les compare explicitement.

## 7. Limites et prévention des blocages

La stratégie n’explore pas un arbre de probabilités. Elle ne prévoit pas tous les tours adverses ni les synergies à long terme. Son but est de rendre une partie cohérente et lisible.

Les tests de simulation contrôlent après chaque action la légalité, les limites des compteurs, les éliminations et l’occupant de Tokyo. Cent graines sont jouées jusqu’à la fin, avec une borne de 5 000 actions pour détecter une boucle. L’humain est piloté par cette même politique dans ces simulations, afin d’automatiser une partie complète. Le contrôleur Android, lui, attend toujours les décisions du joueur humain.

Les paiements des bots utilisent le même `handleBuyCard` que ceux du joueur humain. Le reçu sépare le prix payé des gains dus aux effets, notamment le cas Surcharge réseau : 4 − 3 + 5 = 6 Cellules. Voir [la nouvelle analyse du marché et des coroutines](reanalyse-marche-et-tours.md).
