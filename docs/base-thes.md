# Base de thés : tea-db

La base est le dépôt public **tea-db**. Son `FORMAT.md` fait foi ; ce fichier dit seulement ce que l'app en lit.

L'app lit **seulement le dépôt GitHub public** https://github.com/TheWorms/tea-db, branche `main` :
https://raw.githubusercontent.com/TheWorms/tea-db/main/docs/thes.json, revérifié à chaque ouverture de l'app (ETag) :
les changements poussés sur `main` y apparaissent tout seuls.

## Ce que l'app utilise dans une fiche (`docs/thes.json`, une liste)

| Champ | Usage dans l'app |
|---|---|
| `id` | identifiant de la fiche, gardé avec le thé : quand on en reprend une autre, il retrouve l'ancienne fiche pour remplacer ses notes automatiques (les notes écrites à la main restent) |
| `nom`, `nom_complet`, `alias` | nom affiché ; tous servent à « Récupérer » |
| `famille` | type : green/yellow/matcha → thé vert, black/pu_erh/dark → thé noir, oolong, white → thé blanc, herbal → tisane |
| `origine` | origine (peut être `null`) |
| `temperature_c` | arrondie à 45–95 °C par 5, ou Max ; `null` = infusion à froid, signalée « pas pour la machine » |
| `eau_ml` | arrondie à 100–350 ml par 25 |
| `duree_s` | ramenée à la durée de la machine la plus proche (10 s à 60 min) ; `0` = matcha, signalé |
| `the_g` | dose recalculée pour le volume retenu (`the_g × volume ÷ eau_ml`), mise dans les notes |
| `photo` | téléchargée (relative à `docs/`) quand on récupère le thé ; `null` = vignette de l'app |
| `photo_credit` | écrit sous la photo dans la fiche d'édition quand elle vient de la base (« photo de ta base · … ») ; pas gardé avec le thé |
| `notes`, `theine`, `precaution`, `a_revoir` | notes de départ : dose, « Sans théine », mise en garde, descriptif |

`redirections.json` n'est pas encore utilisé : un thé gardé sous l'`id` d'un doublon retiré de tea-db ne retrouve plus sa fiche, et ses notes automatiques sont alors gardées comme des notes écrites à la main.
