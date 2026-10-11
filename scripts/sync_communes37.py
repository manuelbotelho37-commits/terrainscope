#!/usr/bin/env python3
"""Récupère la liste officielle des communes de l'Indre-et-Loire.
La publication GitHub est protégée contre les mises à jour concurrentes.
Fail-closed: ne publie aucun index si le service est incomplet ou incohérent.
"""
from datetime import datetime, timezone
import json
from pathlib import Path
import requests

API="https://geo.api.gouv.fr/departements/37/communes?fields=nom,code,codesPostaux&format=json"
OUT=Path(__file__).resolve().parent.parent/"communes-37.json"

def main():
    response=requests.get(API,timeout=25,headers={"User-Agent":"TerrainScope/1.0 (communes-37)"})
    response.raise_for_status()
    rows=response.json()
    if not isinstance(rows,list) or not 200<=len(rows)<=350:
        raise ValueError("Liste officielle absente, vide ou vraisemblablement partielle")
    clean=[]
    codes=set()
    for row in rows:
        if not isinstance(row,dict) or not isinstance(row.get("nom"),str) or not isinstance(row.get("code"),str):
            raise ValueError("Commune mal formée")
        code=row["code"].strip()
        name=row["nom"].strip()
        if len(code)!=5 or not code.startswith("37") or not name or code in codes:
            raise ValueError("Code INSEE invalide ou dupliqué")
        codes.add(code)
        postals=row.get("codesPostaux")
        if not isinstance(postals,list) or not postals or any(
                not isinstance(p,str) or len(p)!=5 or not p.startswith("37")
                for p in postals):
            raise ValueError("Codes postaux manquants ou invalides pour "+name)
        clean.append({"nom":name,"code":code,"codesPostaux":sorted(set(postals))})
    # Sortie stable pour minimiser les commits inutiles.
    clean.sort(key=lambda c:(c["nom"].casefold(),c["code"]))
    OUT.write_text(json.dumps(clean,ensure_ascii=False,separators=(",",":"))+"\n",encoding="utf-8")
    print(f"OK : {len(clean)} communes officielles du 37 avec codes postaux, {len(codes)} codes INSEE uniques.")

if __name__=="__main__":
    main()
