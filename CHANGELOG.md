# Journal des versions

## 0.1.21
Relecture complète (Bluetooth, écrans, données, propreté).
- Infusion : « Arrêter » ou la fermeture de la fenêtre pendant la connexion clôt la session sans rien envoyer, et une
  réponse tardive de la machine ne la rouvre plus ; un arrêt est affiché comme tel (« Infusion arrêtée », sans son) et
  plus comme un thé prêt ; une infusion lancée juste après la précédente ne se clôt plus en « Prêt » dès la première
  trame ; pendant « Réessayer », Retour est inactif et la réponse ne touche que cette infusion.
- Notification : le décompte ne passe plus en négatif quand la machine se tait après la fin prévue ; la notification de
  fin de l'infusion précédente est retirée au début d'une nouvelle.
- Liaison : la machine muette après « Arrêter » n'est plus coupée au bout de 3 s ; perte d'appairage reconnue aussi
  sur un échec de sécurité ou une coupure pendant l'abonnement, y compris quand « Infuser » lance la connexion ; après
  un premier appairage raté, la pastille et « Se connecter » refonctionnent ; associer une autre machine arrête la
  relève de l'ancienne ; recherche Bluetooth arrêtée pendant l'association ; les trois « Retour » qui sortent la
  machine de son menu Bluetooth décidés sur son état relu ; lecture des réglages de la machine plus relancée sans geste
  à chaque reconnexion ; « prête » affiché seulement liaison tenue, échecs de « Relancer » affichés.
- Accueil et pastille : la vraie cause (Bluetooth coupé, appairage perdu, pas de machine, connexion en cours) au lieu
  de « Hors de portée », avec quoi faire ; un appui raté sur la pastille se voit aussi hors de l'onglet Machine.
- Onglet Machine : synchronisation et lectures une seule à la fois, suivies même hors de l'écran ; résultat affiché
  près du bouton touché et annoncé ; lecture des préréglages : les thés importés sont comptés ; nombre de préréglages
  écrit retenu (plus de 10 préréglages réaffichés après un changement de langue ou d'unités) ; l'heure est dite réglée
  à la première connexion après le lancement de l'app et à chaque connexion demandée (ou avec « Régler »), plus « à
  chaque connexion ».
- Fiche d'un thé : « Enregistrer » attend une photo encore en chemin (galerie ou base) et garde une synchronisation
  finie entre-temps ; une rotation pendant le chargement de la photo ou l'enregistrement ne perd plus la photo et ne
  crée plus le thé deux fois ; erreurs affichées près du champ en cause et annoncées ; crédit de la photo de la base
  affiché ; « Enregistrer comme thé » montre toujours les réglages venus du mode manuel.
- Base de thés : « Récupérer » préfère le nom exact et ne devine plus sur un début de nom de plusieurs mots (« Thé vert »
  ne remplit plus « Thé vert glacé ») ni une fiche inadaptée à la machine ; recherche avec œ, æ, ß ; thés ajoutés à la
  machine rangés après ceux qui ont déjà une position ; un refus d'accès ne dit plus « le dépôt doit être public ».
- Autorisation Bluetooth : un double appui n'ouvre plus les réglages d'Android par-dessus la demande ; une autorisation
  donnée dans les réglages d'Android est vue au retour.
- Lecteur d'écran : boutons ronds, curseurs (« Eau, 250 ml »), champs, interrupteurs et couleurs nommés ; pastilles,
  choix segmentés, couleurs et onglets du bas disent lequel est sélectionné ; fin d'infusion annoncée.
- Affichage : en paysage ou en grande police, l'accueil et la fenêtre d'infusion défilent (boutons jamais écrasés) ;
  « Remplissage » sur une ligne ; « Prêt » plus en double ; contraste des réglages du bouton Infuser et des alertes
  rouges ; cibles de 48 dp partout ; clignotement d'alerte seulement pendant une alerte, sans recomposer chaque image.
- Mises à jour : seul le retour de l'installation en cours compte (plus de « annulée » venu d'une précédente) ;
  « Installation après l'infusion » affiché.
- Anglais : « tea database » partout, tournures revues, dates à l'anglaise (« 4 Nov at 13:31 »).
- Propreté : code mort, réglages jamais relus et commentaires faux retirés ; docs/base-thes.md à jour (eau de 100 à
  350 ml, usage de l'identifiant, crédit des photos).
- README : Android 13 ou plus récent demandé.

## 0.1.20
- Mises à jour : depuis les Releases GitHub (TheWorms/bru-tea) ; theworms.fr n'est plus utilisé. Les versions jusqu'à
  la 0.1.19 ne regardent que theworms.fr : installer la 0.1.20 une fois à la main.
- Au plus un appel à GitHub toutes les 15 minutes (le bouton « Vérifier » passe outre) ; l'empreinte .sha256 de la
  release reste obligatoire.
- README : section « Télécharger » vers la dernière release.

## 0.1.19
- Base de thés : quand tea-db s'enrichit, une notification l'annonce à l'ouverture (« Base de thés mise à jour ·
  12 nouveaux thés à découvrir. »). Rien au premier chargement.

## 0.1.18
- Eau : 100 ml au minimum (au lieu de 50 ml), comme l'accepte BRU. Curseurs, suggestions de la base et infusions
  ramenés à 100 ml ; un thé déjà enregistré sous 100 ml part à 100 ml.

## 0.1.17
- Trames de la machine lues avec tolérance, comme les versions 0.1.4 à 0.1.15 : état dès 5 octets, longueur et
  somme de contrôle seulement notées (la 0.1.16 les exigeait, sans preuve que la machine les respecte) ; une
  notification n'est découpée que si toutes ses longueurs sont plausibles ; chaque trame reçue est journalisée.
- Suivi d'infusion : l'écran « bonne dégustation » d'une infusion précédente ne clôt plus la nouvelle ; une erreur de la
  machine n'est plus effacée par une acceptation tardive ; liaison retrouvée en cours d'infusion : plus de « reconnexion »
  affiché à tort ; « Réessayer » ne renvoie rien si la fenêtre a été fermée entre-temps (« Fermer » inactif pendant le
  réessai) ; une erreur imprévue termine la session au lieu de la laisser ouverte.
- Liaison : une écriture restée sans réponse coupe et reprend la liaison (avant, chaque écriture suivante échouait) ; plus
  de fenêtre d'appairage à l'ouverture quand la machine n'est plus liée ; lecture des réglages retentée (3 fois au plus).
- Mises à jour : installation reportée à la fin d'une infusion en cours ; une installation encore en copie n'est plus
  prise pour abandonnée.
- Enregistrement d'un thé : erreur affichée, bouton jamais bloqué, retour impossible pendant l'enregistrement ; photos
  inutilisées supprimées seulement après 7 jours ; réglages relus après une erreur de lecture passagère.

## 0.1.16
Scan de stabilité et de propreté (trois relectures indépendantes et lint Android complet).
- Infusion, machine muette pendant qu'elle infuse : décompte local depuis l'acceptation (recalé au début réel de
  l'infusion) ; fin comme l'app de BRU (retour en veille, au menu ou au menu Bluetooth après l'infusion = Prêt) ;
  sans nouvelles à durée + 5 min, fin neutre (sans erreur ni bip). Liaison perdue en route : la session reste ouverte
  (« reconnexion… », notification gardée) et reprend avec la machine ; plus de « rien n'est parti » quand la commande
  est restée sans réponse (« pas de réponse » ; « Réessayer » relit l'état et ne renvoie qu'après un autre état que
  « infusion »). Trames abîmées ignorées pour décider ; notification à plusieurs trames découpée.
- Mise à l'heure et lecture des réglages seulement à la première connexion et sur un geste, plus à chaque reconnexion
  automatique ; plus de reconnexion qui rouvrirait l'appairage toutes les 30 s.
- Bluetooth : appels protégés (une pile Bluetooth qui tombe ne fait plus planter l'app), rappels en retard ignorés,
  MTU remis à zéro à chaque connexion, liaison coupée proprement si l'écran est quitté pendant une connexion ou
  une association, état du Bluetooth suivi en direct à l'association.
- Écrans : infusion, synchronisation, réglages, lecture, association et mise à jour ne sont plus coupés par une
  rotation ou un changement d'onglet ; la fenêtre d'infusion ne laisse plus passer les appuis vers l'écran du dessous ;
  « Arrêter » inactif la première seconde (double appui) ; saisie d'un thé gardée à la rotation, un seul
  enregistrement par appui ; photo de la galerie jamais remplacée par celle de la base ; navigation protégée contre
  les doubles appuis ; autorisations refusées pour de bon : ouverture des réglages de l'app ; cibles de 48 dp.
- Données : réglages illisibles remplacés au lieu de bloquer l'app ; base de thés écrite d'un coup (fichier
  temporaire puis renommage) ; photos bornées en hauteur comme en largeur, écriture vérifiée, ménage des photos
  orphelines au démarrage.
- Mises à jour : empreinte .sha256 obligatoire ; une erreur du serveur n'est plus prise pour « pas de nouvelle
  version » ; un seul téléchargement à la fois ; installation sans confirmation une fois Infusion son propre
  installateur (UPDATE_PACKAGES_WITHOUT_USER_ACTION) ; bouton débloqué si l'installation s'est perdue ; fenêtre
  d'installation différée pendant une infusion.
- Propreté : code mort et dépendances inutiles retirés, commentaires périmés corrigés, README public, JDK et
  chemins privés hors du dépôt.

## 0.1.15
- « Refaire » demande confirmation (« Souhaites-tu refaire « Earl Grey » ? », avec ses réglages) ; le nom du thé n'est plus
  sous la flèche de l'accueil.
- Suivi d'infusion fiabilisé (relecture de la 0.1.14) : une infusion coupée par la liaison reprend seulement si la machine
  l'avait acceptée et pendant sa durée + 5 min, y compris app en arrière-plan (la notification reste « Infusion en cours »,
  « reconnexion… ») ; plus de session « en cours » sans fin ; une machine muette au-delà d'une infusion entière (ou d'une
  minute hors infusion) est tenue pour perdue ; « Réessayer » demande d'abord son état à la machine ; « Arrêter » ne dit
  plus « arrêt envoyé » quand il n'a pas pu partir.
- Langue : la langue de la machine envoyée par défaut est celle de l'app (plus le français d'office) ; un changement de
  langue non confirmé par la machine n'est plus défait par une synchronisation ; le choix est bloqué pendant une
  synchronisation ; noms de langue traduits dans les phrases (« L'écran de la machine est en anglais. ») ; langue de l'app
  bien reconnue avec un téléphone à plusieurs langues ; décimales des onces selon la langue (9.5 oz en anglais).
- Captures : un test produit les écrans principaux en français et en anglais (build/captures) ; corrigés d'après elles :
  « Hello » au lieu de « Good morning », ligne coupée de l'onglet Machine, curseur de durée sans ses 110 repères.

## 0.1.14
- Infusion : plus de fausse erreur « La machine ne répond plus » pendant l'infusion (la machine se tait en infusant) ; une
  infusion coupée reprend son suivi quand la machine répond de nouveau.
- Accueil : le nom du dernier thé reste affiché sous « Refaire », même en affichage « icônes seules ».
- L'app est en français et en anglais : un seul choix de langue (onglet Machine) pour l'app et l'écran de la machine ;
  l'allemand n'est plus proposé. La langue de l'app est aussi réglable dans Android (Applis › Infusion › Langue). La
  machine n'impose plus sa langue à l'app : si elles diffèrent, l'onglet Machine le dit et propose de mettre la machine
  dans la langue de l'app.
- Tous les textes de l'app sont traduits (écrans, messages de la machine, notifications, mises à jour, base de thés). La
  description des thés reste celle de la base, en français ; dose et « Sans théine » des notes reprises de la base
  suivent la langue de l'app.

## 0.1.13
- Supprimer un thé, c'est partout : la croix de l'onglet Machine le supprime aussi de tes thés et de tes favoris (plus de
  « Seulement de la machine », qui laissait des favoris absents de la liste de la machine), et le supprimer dans Thés
  l'enlève de la liste de la machine. Une confirmation est toujours demandée.
- Onglet Thés : au retour de la modification d'un thé, la catégorie choisie et la position dans la liste sont gardées
  (avant : retour sur « Tous », en haut de la liste).

## 0.1.12
- Base de thés : le dépôt s'appelle maintenant https://github.com/TheWorms/tea-db (ex-thea-db). L'app lit la nouvelle
  adresse au lieu de compter sur la redirection de GitHub.

## 0.1.11
- Liaison avec la machine : reprise automatique quand elle se coupe (après 2, 5, 10, 20 s, puis toutes les 30 s, tant que
  l'app est à l'écran ou qu'une infusion est en cours), et liaison muette depuis 15 s tenue pour coupée. Avant, rien ne
  retentait : il fallait se reconnecter à la main.
- Infusion hors de l'app : on peut quitter l'app (ou faire Retour) pendant une infusion ; une notification « Infusion en
  cours » montre l'étape, le décompte et ce que la machine demande, avec « Arrêter » ; à la fin, « Thé prêt » (ou l'échec)
  si l'app n'est pas à l'écran. Autorisation des notifications demandée au lancement d'une infusion.

## 0.1.10
- Nouvelle fiche d'un thé (démo validée le 14/11) : photo en haut, fondue dans la page, type, origine et nom posés dessus ;
  boutons en verre fumé (retour, synchro, favori, crayon) ; pastille de synchro brique tant que la machine n'a pas reçu le
  thé, sauge quand elle l'a reçu (la toucher dit où il en est) ; les trois réglages dans un bandeau ; la dose à part,
  ramenée au volume d'eau du thé ; la description seule ; « Sans théine » en pastille ; « Infuser » rappelle les réglages.
  Plus de ligne « Infos reprises de ta base de thés ».

## 0.1.9
- Onglet Machine : la croix d'un thé « sur la machine » propose « Supprimer le thé » (il disparaît aussi de la liste des
  thés, photo comprise) ou « Seulement de la machine » (il reste dans tes thés). Avant, elle le retirait seulement de la
  machine, sans le dire clairement.

## 0.1.8
- Mises à jour installées par une session d'Android : plus d'écran « Ouvrir / OK » à la fin. Android
  demande « Mettre à jour cette appli ? » tant qu'Infusion n'est pas son propre installateur, puis plus du tout. La fenêtre
  de confirmation n'est ouverte qu'une fois ; annulée, le bouton propose de réessayer.
- À chaque ouverture de l'app (au plus une fois par minute) : recherche d'une nouvelle version et vérification de la base
  de thés.

## 0.1.7
- Base de thés : la description de la fiche est reprise même quand le thé a déjà des notes. La note automatique des thés
  repris de la machine (« Repris du préréglage … ») et celle d'une fiche reprise avant sont remplacées ; des notes écrites
  à la main sont gardées, suivies du descriptif s'il n'y est pas déjà. Avant, seules des notes vides étaient remplies.

## 0.1.6
- Accueil : ce que la machine demande (eau, couvercle, tasse, chambre d'infusion, buse) ou une erreur prend la place de
  « Refaire » et « À la main » et clignote (orange : demande, rouge : erreur) jusqu'à ce que ce soit réglé. Ces messages
  ne sont plus dans l'onglet Machine.
- Mise à jour : un bouton sous le nom de la machine (téléchargement, vérification, puis installation par Android) ; la
  première fois, l'autorisation d'Android est demandée et l'installation reprend au retour.
- Infusion : la fenêtre d'infusion s'ouvre aussi quand rien n'est parti. Machine pas prête : ce qu'elle demande clignote,
  « Relancer » quand c'est réglé ; erreur ou machine hors de portée : « Réessayer » ; réglage refusé : « Modifier le thé ».
- Bip et vibration à chaque nouvelle alerte (Machine → Options de l'app, actifs par défaut).
- Langue et unités de la machine : l'app lit ses réglages à la connexion (commande 42), puis n'envoie que le changement
  en gardant son nombre de préréglages (avant : 0 ou le nombre de thés synchronisés, ce qui pouvait faire refuser le
  réglage ou masquer des préréglages), et relit pour dire si la machine l'a pris. Les unités s'appliquent aussi dans
  l'app (°F, oz).
- Nom du thé : jusqu'à 5 suggestions de la base dès 2 lettres ; en toucher une remplit le thé.
- Base de thés : vérifiée à chaque ouverture de l'app (rien n'est retéléchargé si elle n'a pas changé).
- Favoris de l'accueil : les deux rangées occupent toute la hauteur libre de l'écran.

## 0.1.5
- Mises à jour : la recherche ne s'arrête plus sur une version retirée de theworms.fr (25 numéros essayés à la fois,
  en parallèle) ; nouvelle recherche au retour dans l'app si la dernière date de plus de 30 minutes. Corrige : une 0.1.2
  ne trouvait pas la 0.1.4 quand la 0.1.3 n'était plus en ligne.
- Base de thés : seulement https://github.com/TheWorms/thea-db (plus de choix Dev/Officiel),
  relue au démarrage une fois par jour.
- Pied de l'onglet Machine : seulement la version de l'app.

## 0.1.4
- Base de thés au format réel de thea-db (FORMAT.md) : `docs/thes.json`, photos `docs/assets/photos/catalogue/<id>.webp`.
  « Récupérer » cherche aussi dans `nom_complet` et `alias` ; notes de départ = dose adaptée au volume, « Sans théine »,
  précaution ou « à revoir », puis le descriptif. Matcha et infusions à froid signalés (pas pour la machine).
- Les 431 fiches actuelles se lisent sans rejet (test sur une copie du dépôt).

## 0.1.3
- Nom de la machine : la machine ne renvoie jamais le nom qu'on lui donne (l'app de BRU le garde elle aussi sur le
  téléphone). Le nom choisi est donc gardé dans l'app, envoyé à l'écran de la machine, et n'est plus remplacé par le
  nom Bluetooth générique à chaque connexion (défaut de la 0.1.1). Renommer marche aussi hors connexion.

## 0.1.2
- Base de thés : choix « Dev » (dépôt de travail) ou « Officiel » (github.com/TheWorms/thea-db), plus d'adresse à
  saisir.
- Mises à jour depuis theworms.fr/apk/ (Infusion.<version>.apk et .sha256) : recherche au démarrage, bandeau sur l'accueil,
  téléchargement vérifié (empreinte, paquet, version) puis installateur d'Android. L'app se présente comme
  « Infusion/<version> (Android) » (l'hébergeur refuse la signature « okhttp »).
- Version de l'app en pied de l'onglet Machine.
- Accueil : bloc de la machine moins haut, cartes favoris plus basses, pour voir les deux lignes en entier.

## 0.1.1
- Plus rien n'est écrit sur la machine sans « Synchroniser » : la 0.1.0 renvoyait les thés tout seuls après une
  modification, ce qui remplaçait des préréglages de la machine sans prévenir.
- « Synchroniser » demande confirmation et dit ce qui sera remplacé (préréglages 1 à N, nombre affiché, langue et unités).
- Essai : « Lire les préréglages de la machine » (lecture seule, réponse à confirmer sur la machine) ;
  importe ceux qui se comprennent, sinon montre la réponse brute à copier.
- Nom de la machine relu à chaque connexion (nom Bluetooth donné par la machine, comme l'app de BRU).
- Journal Bluetooth retiré de l'écran (il reste dans la console Android : adb logcat -s Infusion).

## 0.1.0
- Squelette de l'app : Kotlin + Jetpack Compose, 4 onglets (Accueil, Thés, Infuser, Machine).
- Thème clair, sombre ou système ; 6 couleurs (Sauge d'origine par défaut) ; menu en icônes, icônes et texte, ou texte.
- Accueil : bloc de la machine (« Refaire », « À la main ») et favoris sur deux lignes.
- Base locale des thés (Room, schéma 1), plages de la machine (45–95 °C ou Max, 50–350 ml par 25).
- Polices Outfit et Manrope (OFL, docs/licences).
- Thés : ajouter, modifier, supprimer, favoris ; page d'un thé (photo, réglages, notes, crayon pour modifier).
- « Récupérer » : cherche le thé dans la base GitHub (thes.json) d'après le nom, remplit photo et réglages ; sinon saisie à la main.
- Photo depuis la galerie, réduite à 1600 px en WebP, gardée dans les fichiers de l'app.
- Machine → Options de l'app : adresse de la base de thés, état et « Vérifier ».
- Bluetooth : recherche des machines « BRU », association
  (mode appairage de la machine puis confirmation d'Android), mise à l'heure, état toutes les 3 s.
- Infusion depuis la page d'un thé, « Refaire » ou le mode manuel : réglages envoyés puis lancement, écran
  d'infusion (remplissage, infusion avec compte à rebours, service, prêt), messages de la machine, « Arrêter ».
- Durées limitées aux 110 valeurs de la machine (10 s à 60 min) ; eau par 25 ml si le firmware le permet.
- Machine : jusqu'à 10 thés écrits en préréglages (logo vert = reçu et accepté), renommer (12 caractères),
  langue et unités de l'écran, heure. Minuteur retiré (commande inconnue).
- APK signée par une clé propre à l'app, hors dépôt.
