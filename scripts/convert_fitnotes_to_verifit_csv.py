#!/usr/bin/env python3
"""
Convertit un backup FitNotes (fichier .fitnotes, une base SQLite) en CSV
importable directement dans Verifit (Reglages -> Import CSV).

Usage:
    python convert_fitnotes_to_verifit_csv.py MonBackup.fitnotes
    python convert_fitnotes_to_verifit_csv.py MonBackup.fitnotes -o data.csv
    python convert_fitnotes_to_verifit_csv.py MonBackup.fitnotes --since 2026-09-04

Par defaut, le script convertit TOUT l'historique du backup (comportement
recommande - voir l'avertissement ci-dessous). L'option --since permet de ne
garder que les entrees a partir d'une date donnee (inclus), pour un usage
avance seulement (voir plus bas pourquoi ce n'est PAS le mode a utiliser pour
une simple resynchronisation).

------------------------------------------------------------------------
IMPORTANT - LIS CECI AVANT D'IMPORTER LE CSV GENERE DANS VERIFIT :

L'import CSV de Verifit (Reglages -> Import CSV) REMPLACE ENTIEREMENT les
donnees actuellement dans l'app (DataStorage.csvToSets() fait un sets.clear()
puis reconstruit tout depuis le CSV - c'est un restore complet, pas un ajout.
L'app affiche d'ailleurs elle-meme l'avertissement "This will overwrite all
saved data" avant de continuer).

Consequence concrete : si tu utilises --since pour ne generer qu'un delta
(ex. juste ta derniere seance) et que tu l'importes tel quel, TOUT LE RESTE
DE TON HISTORIQUE DANS VERIFIT SERA EFFACE - il ne restera que ce delta.

Le mode par defaut (sans --since, tout l'historique) est le mode sur qui
reconstruit l'etat complet et correct a chaque import : convertis TOUT le
backup FitNotes a chaque fois que tu veux resynchroniser Verifit, meme si
l'essentiel des lignes existe deja - c'est voulu et sans risque, puisque
l'import remplace de toute facon tout.

N'utilise --since que si tu sais exactement ce que tu fais (ex. tu es en
train de fusionner ce delta a la main avec un autre CSV avant import - voir
`docs/fitnotes-fork-plan.md`, section "Synchronisation delta" pour un exemple
concret de cette situation, geree avec Claude au cas par cas).
------------------------------------------------------------------------

Notes techniques (pourquoi le script est ecrit ainsi) :
- L'ordre des colonnes du CSV genere est "Date,Exercise,Category,
  Weight (kg),Reps,Comment" - Weight AVANT Reps. Cet ordre n'est pas
  arbitraire : DataStorage.csvToSets() (cote app Verifit) a un bug de
  nommage de variables qui inverse silencieusement les deux colonnes en les
  passant au constructeur de WorkoutSet - un CSV avec Reps avant Weight
  importerait donc TOUTES les series avec poids et repetitions inverses.
  Ne change JAMAIS cet ordre sans avoir aussi corrige ce bug cote app.
- Seules les series "completes" et basees sur poids x repetitions sont
  gardees (is_complete=1 et unit=0 dans FitNotes) - Verifit n'a pas de
  notion de serie planifiee/incomplete ni de serie basee sur temps/distance.
- Les commentaires FitNotes (table Comment, owner_type_id=1) sont rattaches
  a leur serie par _id de training_log et concatenes avec " / " si plusieurs
  existent pour la meme serie (rare, mais pour ne rien perdre silencieusement).
- Une virgule dans un nom d'exercice/categorie/commentaire est remplacee par
  " - " : CSVFile.java (cote app) decoupe chaque ligne sur "," de facon
  naive, une vraie virgule ferait donc glisser toutes les colonnes suivantes.
"""

import argparse
import os
import re
import sqlite3
import sys


def sanitize(s):
    if s is None:
        return ""
    s = str(s)
    s = s.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
    # CSVFile.java (cote app Verifit) decoupe chaque ligne sur "," de facon
    # naive - une vraie virgule ferait glisser toutes les colonnes suivantes.
    s = re.sub(r",\s*", " - ", s)
    return s.strip()


def fmt_num(x):
    # Evite le bruit "40.0" pour les nombres entiers, garde les decimales
    # sinon (ex. poids de 47.5 kg).
    f = float(x)
    if f == int(f):
        return str(int(f))
    return ("%g" % f)


def convert(backup_path, output_path, since_date):
    con = sqlite3.connect(backup_path)
    con.row_factory = sqlite3.Row
    cur = con.cursor()

    query = """
        select t._id as tid, t.date, t.metric_weight, t.reps, t.exercise_id,
               e.name as exercise_name, e.category_id, c.name as category_name
        from training_log t
        join exercise e on e."_id" = t.exercise_id
        join Category c on c."_id" = e.category_id
        where t.is_complete = 1 and t.unit = 0
    """
    params = ()
    if since_date:
        query += " and t.date >= ?"
        params = (since_date,)
    query += " order by t.date asc, t._id asc"

    cur.execute(query, params)
    rows = cur.fetchall()

    # owner_type_id=1 comments sont indexes par training_log._id (verifie sur
    # des donnees reelles lors de la construction de ce script).
    cur.execute("select owner_id, comment from Comment where owner_type_id = 1")
    comments_by_tid = {}
    for owner_id, comment in cur.fetchall():
        comments_by_tid.setdefault(owner_id, []).append(comment)

    kept = 0
    comments_attached = 0
    exercises_seen = set()
    dates_seen = set()

    with open(output_path, "w", encoding="utf-8", newline="\n") as f:
        # IMPORTANT : voir le commentaire en tete de fichier - ne pas changer
        # cet ordre de colonnes (Weight avant Reps) sans corriger d'abord le
        # bug correspondant dans DataStorage.csvToSets().
        f.write("Date,Exercise,Category,Weight (kg),Reps,Comment\n")
        for r in rows:
            date = r["date"]
            exercise = sanitize(r["exercise_name"])
            category = sanitize(r["category_name"])
            reps = fmt_num(r["reps"])
            weight = fmt_num(r["metric_weight"])
            raw_comments = comments_by_tid.get(r["tid"], [])
            comment = sanitize(" / ".join(raw_comments)) if raw_comments else ""
            if raw_comments:
                comments_attached += 1

            f.write(f"{date},{exercise},{category},{weight},{reps},{comment}\n")
            kept += 1
            exercises_seen.add(exercise)
            dates_seen.add(date)

    incomplete_query = "select count(*) from training_log where is_complete = 0"
    time_distance_query = "select count(*) from training_log where is_complete = 1 and unit != 0"
    if since_date:
        incomplete_query += " and date >= ?"
        time_distance_query += " and date >= ?"
        cur.execute(incomplete_query, params)
    else:
        cur.execute(incomplete_query)
    dropped_incomplete = cur.fetchone()[0]

    if since_date:
        cur.execute(time_distance_query, params)
    else:
        cur.execute(time_distance_query)
    dropped_time_distance = cur.fetchone()[0]

    con.close()

    print(f"Sets ecrites             : {kept}")
    print(f"Exercices distincts      : {len(exercises_seen)}")
    print(f"Jours distincts          : {len(dates_seen)}")
    if dates_seen:
        print(f"Plage de dates           : {min(dates_seen)} -> {max(dates_seen)}")
    print(f"Series avec commentaire  : {comments_attached}")
    print(f"Ecartees (incompletes)   : {dropped_incomplete}")
    print(f"Ecartees (temps/distance): {dropped_time_distance}")
    print(f"Fichier genere           : {output_path}")

    if since_date:
        print()
        print("!! Rappel : ce CSV ne contient qu'un DELTA (--since utilise).")
        print("!! L'importer tel quel dans Verifit EFFACERA le reste de ton")
        print("!! historique (l'import remplace toutes les donnees de l'app).")
        print("!! Voir l'avertissement en tete de ce script avant de continuer.")


def main():
    parser = argparse.ArgumentParser(
        description="Convertit un backup FitNotes (.fitnotes) en CSV importable dans Verifit."
    )
    parser.add_argument("backup", help="Chemin vers le fichier .fitnotes (backup FitNotes)")
    parser.add_argument(
        "-o", "--output",
        help="Chemin du CSV a generer (defaut : a cote du backup, meme nom + .csv)",
        default=None,
    )
    parser.add_argument(
        "--since",
        metavar="YYYY-MM-DD",
        default=None,
        help=(
            "Usage avance seulement : ne garder que les series a partir de cette date "
            "(incluse). Voir l'avertissement en tete de ce fichier avant d'utiliser "
            "cette option - NE PAS importer le resultat directement dans Verifit sans "
            "le fusionner d'abord avec un CSV complet existant."
        ),
    )
    args = parser.parse_args()

    if not os.path.isfile(args.backup):
        print(f"Erreur : fichier introuvable : {args.backup}", file=sys.stderr)
        sys.exit(1)

    output_path = args.output
    if output_path is None:
        base = os.path.splitext(os.path.basename(args.backup))[0]
        output_path = os.path.join(os.path.dirname(os.path.abspath(args.backup)), f"{base}_verifit_import.csv")

    try:
        convert(args.backup, output_path, args.since)
    except sqlite3.DatabaseError as e:
        print(f"Erreur : impossible de lire '{args.backup}' comme base FitNotes ({e}).", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
