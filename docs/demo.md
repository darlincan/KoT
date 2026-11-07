# Scénario de démonstration

Scénario rejouable avec la graine **343** et le monstre **Voltrax**. Les gestes ci-dessous ont été rejoués sur émulateur : mêmes dés, mêmes dégâts et même résultat final. Seule l'ouverture du Marché noir sans achat au tour 2 n'a pas été rejouée dans cette séquence ; elle ne modifie pas la partie.

Ces gestes montrent toutes les étapes du scénario, mais la partie est perdue à la fin : Voltrax est éliminé et Mecha-Lynx l'emporte. C'est voulu : cette graine montre tous les écrans en peu de gestes.

## Préparation

1. Dans `app/src/main/kotlin/com/neonkaiju/kot/di/AppModule.kt`, remplacer temporairement la graine (ne pas commiter ce changement) :

   ```kotlin
   fun provideSeedProvider(): SeedProvider = SeedProvider { 343L }
   ```

2. Installer l'application : `./gradlew :app:installDebug`.
3. Partir d'une application sans partie en cours (`adb shell pm clear com.neonkaiju.kot`).
4. Dans **Réglages**, activer **Bots rapides** pour réduire l'attente : les bots jouent environ 190 actions, soit environ 1 min 20 à 400 ms par action. Laisser **Compte à rebours Fuir ou Rester** désactivé. La vitesse des bots ne change pas leurs décisions.

Les dés sont numérotés de gauche à droite, puis de haut en bas. Il faut attendre **À TON TOUR** avant de toucher un dé : pendant le tour d'un bot, les commandes sont bloquées.

## Déroulement

| Étape | À faire | Ce que l'on doit voir | Ce que cela montre |
| --- | --- | --- | --- |
| Accueil | **Nouvelle partie** | Écran d'accueil et ses 4 entrées | Écran 01 |
| Choix du monstre | Garder Voltrax, **Lancer l'assaut** | Voltrax sélectionné | Écran 02 |
| Tour des bots | Attendre | Explication du bot, Ferrodon entre dans la Tour | Bots, actions visibles |
| Tour 1 | Dés `1 1 ♥ ⚡ 1 3` : garder 1, 2, 5, **Relancer** | `1 1 🐾 3 1 ♥` | Garder et relancer |
| Tour 1 | Garder 3, **Relancer** | Résolution automatique : ★1, ⚡1, Surcharge 2 | Résolution |
| Tour 1 | **Fin du tour** | Voltrax perd 2 ♥ avant son tour suivant (♥8) | Griffes de l'occupant |
| Tour 2 | Dés `1 2 1 3 3 2` : garder 1, 3, **Relancer** | `1 🐾 1 ♥ 3 🐾` | |
| Tour 2 | Garder 2, 6, **Relancer** | `1 🐾 1 1 1 🐾`, puis écran **Palier 3 : choisir un pouvoir** | Palier de Surcharge, écran 06 |
| Tour 2 | **Équiper** Châssis renforcé | Voltrax entre dans la Tour : ★4, Surcharge 4 | Entrée dans la Tour |
| Marché noir | Ouvrir **Marché noir**, puis **Fermer le marché** sans acheter | Trois cartes et leurs prix | Écran 04 |
| Reprise | Touche Accueil d'Android, puis rouvrir l'application | Même écran, même partie | Arrière-plan et reprise |
| Tour 2 | **Fin du tour** | Les bots attaquent | |
| Fuir ou Rester | Ferrodon inflige 4 dégâts, il reste 4 ♥ : **Fuir la Tour** | Conséquences de chaque choix, aucune limite de temps | Écran 05 |
| Tour 3 | Dés `⚡ 3 3 1 🐾 2` à ♥3 : garder 5, **Relancer** | `🐾 ⚡ 🐾 1 🐾 ⚡` | |
| Tour 3 | Garder 1, 3, **Relancer**, puis **Fin du tour** | ★5, ⚡2, Voltrax de retour dans la Tour | |
| Fuir ou Rester | Ferrodon inflige 1 dégât, il reste 2 ♥ : **Fuir la Tour** | | Écran 05 |
| Tour 4 | Dés `1 ⚡ 2 1 2 2` à ♥1 : **Relancer** sans garder | `3 🐾 ⚡ ♥ 3 ⚡` | |
| Tour 4 | Garder 2, 4, **Relancer** | `1 🐾 ⚡ ♥ 1 1`, puis écran **Palier 6** | Palier de Surcharge |
| Tour 4 | **Équiper** Arc électrique | ♥2, ★7, ⚡3 | |
| Achat | **Marché noir**, **Sélectionner** Contact clandestin, **Acheter la sélection**, **Confirmer le paiement** | Reçu : `3 − 3 = 0 Cellules` | Achat confirmé |
| Fin du tour | **Fermer le marché**, puis **Fin du tour** | Les bots terminent la partie | |
| Fin de partie | Attendre | **Dernier survivant**, Mecha-Lynx (★6, ♥6) ; Voltrax éliminé à ★7 ; graine 343 | Écran 07 |

## Après la démonstration

Rétablir la graine d'origine dans `AppModule.kt` : `git checkout -- app/src/main/kotlin/com/neonkaiju/kot/di/AppModule.kt`.

Si les dés affichés diffèrent du tableau, c'est qu'un geste a été oublié ou que le moteur a changé. Recommencer depuis la préparation.
