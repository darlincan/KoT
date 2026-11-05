# Nouvelle analyse : marché, ressources et tours des bots

Référence : `specifications.md`, §2.1, §6, RULE-09 à RULE-38 et §9. La sélection individuelle des dés et la résolution automatique sont conservées. Le document utilisateur décrit des captures ; aucune nouvelle image n’a servi à identifier une carte précise. Les causes ci-dessous distinguent donc le code confirmé des hypothèses sur la partie observée.

## 1. Marché noir : ce qui est confirmé

Le calcul initial de `GameEngine.handleBuyCard` retirait déjà le coût réel, puis appliquait les effets ACTION. `EffectInterpreter.computeCardCost` tient compte des réductions détenues, avec le plancher existant de 1 Cellule. Le moteur n’ajoute pas spontanément de Cellules à tout achat.

Le cas **4 → 6** est reproductible avec `surcharge_reseau` : coût 3, effet immédiat +5, donc **4 − 3 + 5 = 6**. Cela ne prouve pas que cette carte était celle du test utilisateur. Un achat à 3 sans gain immédiat, par exemple `coeur_nucleaire`, doit laisser **1 Cellule**. Ce pouvoir donne de l’énergie au début d’un tour ultérieur, pas au moment de l’achat.

Les spécifications prévoient exactement **trois cartes visibles** (RULE-02 et RULE-31). Chaque achat est immédiatement remplacé depuis la pioche. Plusieurs achats par tour sont légaux si l’énergie suffit. Une carte ACTION est résolue puis défaussée ; une carte POUVOIR reste dans l’inventaire. Renouveler coûte 2 Cellules (RULE-32).

### Problèmes d’interface confirmés et corrections

- L’ancien bouton « Acheter » exécutait directement l’achat. Il n’existait pas de sélection distincte ni de confirmation. Les cartes et le stock changeaient immédiatement, ce qui pouvait être pris pour une sélection sans paiement.
- Le marché n’affichait pas de reçu distinguant le paiement du gain dû à l’effet.
- L’ancienne interface n’attachait pas la confirmation à la révision de l’état affiché.

Le nouveau parcours est : **Sélectionner → Acheter la sélection → Confirmer le paiement**. Changer de sélection ou annuler ne transmet aucune action d’achat. Le renouvellement demande également une confirmation. La fermeture ne transmet qu’un événement de navigation.

`GameEngine.quoteCard` fournit le coût réel, le stock avant paiement et la révision de l’état, sans réduire ni modifier la partie. Le bouton d’achat est commandé par `legalActions`. La confirmation affiche le solde **avant effet** et le texte de la carte.

Après l’opération, `MarketReceipt`, enregistré dans `GameState.lastMarketReceipts`, conserve le paiement, la variation d’énergie due aux effets, le solde final et les messages des effets. Exemple affiché : `Surcharge réseau : 4 − 3 + 5 (effet) = 6 Cellules`. Le reçu appartient à son acheteur ; un achat de bot ne remplace pas celui de l’humain.

La confirmation est consommée avant l’envoi. `reduceIfCurrent` ignore une commande dont la révision est périmée, même si la même carte revient plus tard au marché. `reduce`, lui, continue à rejeter les actions illégales comme prévu par les spécifications. Le contrôleur Android vérifie également le décideur humain, la phase, la légalité et la révision courante.

## 2. Chiffres, points et gains visibles

Les règles du projet sont : au moins trois chiffres identiques ; un triple de n donne n points ; chaque dé identique supplémentaire donne un point de plus. Une paire ne donne rien. Les 1 supplémentaires n’ajoutent pas de Méchanceté au-delà des 2 du triple ; les 2 donnent 1 de Méchanceté par triple ; les 3 n’en donnent pas.

Les points sont ajoutés lors de la résolution des résultats définitifs, pas à chaque conservation ou relance. Le nouveau bilan persistant distingue `ResolutionGains.numbersVp` et `tokyoVp`, avec le reste éventuel attribué aux effets. Les plafonds sont appliqués aux gains réellement ajoutés, pas seulement aux nombres théoriques.

Le journal précise maintenant les combinaisons : par exemple `3 dés 1 : gagne +1 ★ avec les dés ; +2 Surcharge`. L’entrée dans Tokyo possède son propre message et son propre montant. Le bilan reste visible sur la fiche du joueur pendant les tours des bots.

Hypothèses possibles pour une absence de point lors du test : moins de trois 1 à la résolution finale ; bilan auparavant masqué par la troncature du journal ; transformation d’un 1 par un effet avant résolution. Ces possibilités ne sont pas présentées comme une reproduction de la partie utilisateur faute d’état sauvegardé correspondant.

## 3. Griffes et santé

Dans Tokyo, les griffes ciblent les autres survivants. Hors de Tokyo, elles ciblent uniquement son occupant. Si Tokyo est vide, elles ne blessent personne ; l’entrée obligatoire se produit ensuite et donne son propre point.

Le nombre de griffes et les protections sont évalués par le moteur ; les PV réellement stockés sont modifiés par `EffectInterpreter.applyDamage`. Le journal d’attaque précise l’attaquant, la cible et les PV avant/après, par exemple `Voltrax → Mecha-Lynx : 3 dégât(s), 10 → 7 ♥`.

Rester à Tokyo après une blessure **ne soigne pas** et n’annule pas les dégâts. Une griffe peut être entièrement absorbée par un pouvoir de protection ; l’absence de perte de PV n’est donc pas toujours un défaut. Les tests couvrent ces cas sans supposer quel était l’occupant de la partie décrite.

## 4. Méchanceté et Surcharge

Le tableau de vocabulaire de §2.1 indique explicitement : **Méchanceté = Surcharge** dans le thème Néon Kaiju. Ce ne sont pas deux ressources. Le stockage est `PlayerState.malice`, avec une limite de 10, sans dépense ni recul ; les paliers 3, 6 et 10 ouvrent un choix de pouvoir disponible, une seule fois par palier et par joueur.

La jauge mentionne les deux noms pour éviter la confusion. Les fiches adverses montrent aussi leur valeur `S`. Les points de victoire (`vp`), PV (`hp`) et Cellules (`energy`) restent distincts.

## 5. Architecture réellement présente

Le projet utilise un **flux unidirectionnel avec un moteur réducteur**, piloté par une Activity Compose. Ce n’est pas une implémentation complète de MVVM : aucune classe ViewModel n’est utilisée pour posséder l’état ou orchestrer les tours. La présence d’une dépendance `lifecycle-viewmodel-compose` ne suffit pas à constituer MVVM.

```text
Écrans Compose (:feature:game)
    → événements / sélection et confirmation locales
MainActivity (:app) — état Compose, navigation, sauvegarde, coroutine du bot
    → actions validées et datées par leur révision
GameEngine (:core:engine) — legalActions, reduce, transitions, devis, reçus
    ↔ modèles immuables et sérialisables (:core:model)
    → catalogue et interpréteur des effets (:core:content)
    ← décisions HeuristicBotPolicy et attente BotTurnPresenter (:core:ai)
    → nouvel état → MainActivity → écrans
```

Les points, dégâts, soins et paiements restent hors des composables. Le marché lit le coût fourni par le moteur ; il n’applique pas sa propre règle de prix. La sélection et les confirmations locales ne sont pas des règles de jeu et ne modifient pas `GameState`.

Les règles et les bots sont testés en JVM, sans Android. Le moteur n’importe pas Android. Les limites de l’architecture sont la concentration de la navigation et de la persistance dans MainActivity et l’absence de tests d’interface automatiques pour les interactions Compose. Une extraction ultérieure du contrôleur et du stockage vers un ViewModel serait possible, mais aucune migration générale n’est nécessaire pour corriger le paiement.

## 6. Coroutines et rythme des bots

Les coroutines étaient déjà présentes dans `MainActivity.LaunchedEffect` avec `delay`. Elles ne sont pas ajoutées artificiellement au calcul des règles.

`BotTurnPresenter.nextAction` est maintenant une fonction suspendue testable. Elle choisit une action depuis l’état immuable, attend sans bloquer le thread principal avec `delay`, puis vérifie à nouveau que cet état est toujours courant. L’annulation est propagée ; aucune exception de cancellation n’est avalée.

En vitesse normale :

| Étape | Attente de présentation |
| --- | --- |
| Changement individuel de conservation | 450 ms |
| Relance, arrêt/résolution, achat, choix de tuile ou de Tokyo | 1 800 ms |
| Fin de tour, pour laisser lire le résultat | 2 200 ms |
| Mode rapide, toutes actions | 400 ms |

L’effet Compose dépend de l’état, de la route, de la vitesse et de l’état RESUMED de l’Activity. Une pause, un retour à l’accueil, un arrière-plan ou une fin de partie annule l’attente. Le retour au jeu reprend depuis l’état courant ; aucun lancer supplémentaire n’est créé pour « rattraper » un délai. Un seul effet orchestre une action à la fois et les bots avancent dans l’ordre imposé par le moteur. `viewModelScope` n’est pas utilisé puisqu’aucun ViewModel ne possède cette orchestration.

Avant un EndTurn, l’écran montre les gains de la résolution pendant l’attente de 2,2 secondes, ainsi que le motif d’achat ou de fin de tour. Les commandes humaines restent désactivées pour une décision de bot.

### Limites conservées et erreurs

Le calcul synchrone du moteur reste sur le thread appelant ; les tests portent sur sa validité, pas sur un budget de performance Android. La sauvegarde JSON via AtomicFile reste synchrone dans `persist` : une partie très longue ou un stockage lent peut provoquer un ralentissement, et une erreur d’écriture reste une limite à traiter. Déplacer cette écriture vers IO nécessite une file sérialisée et une politique de fin de vie pour ne pas introduire des sauvegardes concurrentes ou perdues ; ce changement n’est pas présenté comme réalisé ici.

Les tests de cancellation vérifient réellement des jobs et délais virtuels du composant BotTurnPresenter. Ils ne remplacent pas un test instrumenté du bouton Retour ou du cycle de vie Android.

## 7. Validation et portée

Les nouveaux scénarios sont dans :

- `MarketTest` : paiement 4−3=1, solde exact, refus sans énergie, reproduction 4−3+5=6, double confirmation périmée, sélection de devis sans effet, paiement de la seule sélection confirmée, renouvellement payé une seule fois, coût réduit identique au reçu et sérialisation.
- `ObservedScenariosTest` : trois griffes font réellement passer 10→7 PV ; rester conserve 7 PV ; Tour vide ; griffe absorbée ; paliers 3/6/10 ; plafond de Méchanceté.
- `VisibleGainsTest` et le test paramétré des chiffres : séparation des points de dés et de Tokyo et gains réellement stockés, y compris aux plafonds.
- `BotTurnPresenterTest` : délai sans blocage en temps virtuel, émission unique, cancellation sans action, révision de l’état après attente et vitesses de présentation.
- Les tests existants conservent les 100 parties simulées, le retour au joueur humain et le catalogue complet.

La fermeture du marché est un changement de route sans action de moteur ; le contrôle de cette liaison relève de la lecture du contrôleur tant qu’un test instrumenté n’a pas été exécuté. Aucun succès de sélection/confirmation par clic physique, ni de fluidité ou de lisibilité sur appareil, n’est revendiqué sans exécution correspondante. Les captures de la session précédente ne constituent pas une validation de ce nouveau marché.

Les résultats exacts de compilation et de tests de cette itération sont ajoutés au bilan `VALIDATION.md` après exécution.
