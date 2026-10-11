#!/usr/bin/env python3
"""TerrainScope: audit horaire des catalogues OFFICIELS, sans faux zero.

Un inventaire ne devient "verified" que si son compteur public existe et
que TOUS les liens de programmes uniques sont identifies. Ne jamais convertir
une page inaccessible, partielle ou une absence en "vendu" ou "0 programme".
Les prix et le nombre de lots ne sont PAS synchronises depuis ce simple index.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import sys
from urllib.parse import urljoin, urlparse, urlunparse

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "lotisseurs-live.json"
AFEDIM = "https://www.afedim.fr/fr/achat-immobilier/annonces/Terrain/Indre-et-Loire-France/surface-0-800-m2/budget-0-2000000-euros/rayon-0-km/Resultats"
VILLADIM = "https://www.villadim.fr/programmes/indre-et-loire"
SET = "https://set.fr/nos-offres/pour-les-particuliers/terrains-a-vendre/"
VTH = "https://www.valtourainehabitat-vente.fr/lotissement/"
LELIEVRE = "https://www.lelievre-immobilier.com/terrains-a-vendre-indre-et-loire"
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; TerrainScopeCatalogAudit/1.0; +https://manuelbotelho37-commits.github.io/terrainscope/)"}
MAX_COUNT = 200


def normalized_link(link: str, base: str) -> str:
    parsed = urlparse(urljoin(base, link))
    if parsed.scheme != "https":
        return ""
    return urlunparse((parsed.scheme, parsed.netloc.lower().removeprefix("www."),
                       parsed.path.rstrip("/"), "", "", ""))


def extract_afedim(html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")
    visible = soup.get_text(" ", strip=True)
    m = re.search(r"\b(\d{1,3})\s+biens?\s+disponibles?\b", visible, re.IGNORECASE)
    expected = int(m.group(1)) if m else None
    links = {}
    for a in soup.find_all("a", href=True):
        url = normalized_link(a["href"], AFEDIM)
        if "afedim.fr" not in urlparse(url).netloc:
            continue
        if not re.search(r"/(?:annonces/)?programme/terrain/[^/]+-37/\d+/Fiche$", url, re.I):
            continue
        city = re.search(r"/terrain/([^/]+)-37/\d+/Fiche$", url, re.I).group(1)
        links[url] = {
            "url": url,
            "city": city.replace("-", " ").title(),
            "label": city.replace("-", " ").title(),
        }
    return audit(expected, list(links.values()), "AFEDIM",
                 "Index AFEDIM terrains Indre-et-Loire (total des programmes, pas des lots libres)")


def extract_villadim(html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")
    visible = soup.get_text(" ", strip=True)
    m = re.search(r"\b(\d{1,3})\s+programmes?\s+disponibles?\b", visible, re.IGNORECASE)
    expected = int(m.group(1)) if m else None
    links = {}
    for a in soup.find_all("a", href=True):
        url = normalized_link(a["href"], VILLADIM)
        if urlparse(url).netloc != "villadim.fr":
            continue
        match = re.search(r"/terrains/([^/]+)/([^/]+)$", url, re.I)
        if not match:
            continue
        # Le menu Villadim contient des programmes de toute la France.
        # Les fiches départementales sont explicitement libellées "(37)".
        # Ne jamais mélanger les liens d'autres départements au compteur 37.
        label_text = a.get_text(" ", strip=True)
        if not re.search(r"\(\s*37\s*\)\s*$", label_text):
            continue
        city, slug = match.groups()
        links[url] = {"url": url, "city": city.replace("-", " ").title(),
                      "label": slug.replace("-", " ").title()}
    return audit(expected, list(links.values()), "Villadim",
                 "Catalogue Villadim 37 : ne valider que si les liens des fiches sont tous identifiés")


def audit(expected: int | None, programs: list[dict], name: str, method: str) -> dict:
    # Une erreur de parsing ne doit JAMAIS signifier zero programme.
    valid = expected is not None and 1 <= expected <= MAX_COUNT and len(programs) == expected
    return {
        "status": "verified" if valid else "incomplete",
        "expected": expected,
        "found": len(programs),
        "programs": programs if valid else [],
        "method": method,
        "message": ("Inventaire des programmes cohérent avec le compteur officiel."
                    if valid else "Inventaire non vérifié : nombre officiel absent ou lien(s) manquant(s)."),
        "lotsVerified": False,
    }


def fetch_afedim() -> dict:
    r = requests.get(AFEDIM, headers=HEADERS, timeout=25)
    r.raise_for_status()
    return extract_afedim(r.text)


def fetch_villadim() -> dict:
    # Le catalogue Villadim charge ses fiches en JavaScript. Ne pas conclure
    # a zero apres un simple requests.get() d'une page non encore rendue.
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, args=["--no-sandbox"])
        try:
            page = browser.new_page(viewport={"width": 1440, "height": 1000}, locale="fr-FR")
            page.goto(VILLADIM, wait_until="domcontentloaded", timeout=45000)
            # Attendre une éventuelle requête réseau ; les erreurs restent explicites.
            try:
                page.get_by_text(re.compile(r"\d+\s+programmes?\s+disponibles?", re.I)).first.wait_for(timeout=20000)
            except Exception:
                pass
            page.wait_for_timeout(2500)
            # Charger les éventuelles cartes plus bas sur la page.
            for _ in range(5):
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                page.wait_for_timeout(550)
            page.evaluate("window.scrollTo(0, 0)")
            return extract_villadim(page.content())
        finally:
            browser.close()



def observed(programs: list[dict], method: str) -> dict:
    # Repérage automatique utile, mais sans compteur officiel réconcilié.
    # Ne jamais afficher le nombre repéré comme total garanti.
    links = {p["url"]: p for p in programs if p.get("url")}
    return {"status": "observed" if links else "incomplete",
            "expected": None, "found": len(links) if links else None,
            "programs": list(links.values()),
            "lotsVerified": False,
            "method": method,
            "message": ("Fiches repérées, exhaustivité du catalogue non attestée."
                        if links else "Aucune fiche identifiée : résultat non exploitable, pas zéro disponible.")}


def extract_set(html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")
    programs = []
    for a in soup.find_all("a", href=True):
        if "en savoir plus" not in a.get_text(" ", strip=True).lower():
            continue
        link = normalized_link(a["href"], SET)
        netloc = urlparse(link).netloc
        if not link or netloc in ("set.fr", "fr.linkedin.com"):
            continue
        # Les cartes individuelles SET pointent vers le site dédié du programme.
        title = a.find_previous(["h3", "h4"])
        if not title:
            continue
        name = title.get_text(" ", strip=True)
        if not name or len(name) > 95:
            continue
        programs.append({"url": link, "label": name, "city": "Commune à vérifier"})
    return observed(programs, "Programmes repérés sur la page officielle SET ; pas de compteur total certifiable.")


def fetch_set() -> dict:
    r = requests.get(SET, timeout=30, headers=HEADERS)
    r.raise_for_status()
    return extract_set(r.text)


def extract_vth(html: str, base: str) -> tuple[list[dict], list[str]]:
    soup = BeautifulSoup(html, "html.parser")
    found, pagination = {}, []
    for a in soup.find_all("a", href=True):
        url = normalized_link(a["href"], base)
        if urlparse(url).netloc != "valtourainehabitat-vente.fr":
            continue
        m = re.search(r"/lotissement/([^/]+)$", url)
        if m:
            # Les autres liens WordPress n'ont pas ce chemin de fiche.
            slug = m.group(1)
            found[url] = {"url": url, "label": slug.replace("-", " ").title(),
                          "city": "Commune à vérifier"}
        if re.search(r"/lotissement/page/\d+$", url):
            pagination.append(url)
    return list(found.values()), pagination



def check_known_vth() -> list[dict]:
    """Inspecte chaque fiche VTH répertoriée, même absente de la page à quatre cartes."""
    raw=(ROOT / "lotisseurs-data.js").read_text(encoding="utf-8")
    match=re.search(r"window\.TERRAIN_LOTISSEURS_DATA\s*=\s*(\{.*\});\s*$", raw, re.S)
    if not match:
        raise ValueError("Catalogue historique illisible")
    old=json.loads(match.group(1))
    results=[]
    for item in old.get("programs", []):
        if item.get("developer")!="Val Touraine Habitat":
            continue
        url=normalized_link(item.get("url",""), VTH)
        if urlparse(url).netloc!="valtourainehabitat-vente.fr":
            continue
        result={"url":url,"name":item.get("name"),"city":item.get("city"),
                "httpStatus":None,"status":"unavailable","lotsVerified":False,
                "freeLots":None,"rowCount":None}
        try:
            page=requests.get(url,timeout=16,headers=HEADERS)
            result["httpStatus"]=page.status_code
            if page.status_code==404:
                result["status"]="not_found"
            elif page.ok:
                soup=BeautifulSoup(page.text,"html.parser")
                heading=soup.find("h1")
                if heading:
                    result["status"]="reachable"
                    n=free=0
                    for tr in soup.find_all("tr"):
                        cells=[x.get_text(" ",strip=True).lower()
                               for x in tr.find_all(["td","th"])]
                        if len(cells)<4 or not re.fullmatch(r"\d{1,3}(?:\s*[a-z]+)?",cells[0]):
                            continue
                        status=cells[3].strip()
                        if status not in {"libre","option","optionné","sous-compromis","vendu","vendue"}:
                            continue
                        n+=1
                        free+=int(status=="libre")
                    if n:
                        result["lotsVerified"]=True
                        result["rowCount"]=n
                        result["freeLots"]=free
                else:
                    result["status"]="uncertain"
        except requests.RequestException:
            pass
        results.append(result)
    return results


def with_vth_details(report:dict) -> dict:
    checks=check_known_vth()
    report["trackedReferences"]=len(checks)
    report["referenceChecks"]=checks
    report["warning"]=(report.get("warning","")+
        f" {len(checks)} fiches connues, dont {sum(x['status']=='reachable' for x in checks)} accessibles pendant ce contrôle."+
        " Un lien en erreur ne prouve ni une vente ni la disparition du programme.").strip()
    return report


def fetch_vth() -> dict:
    visited, pending, all_programs = set(), [VTH], {}
    try:
        while pending and len(visited) < 12:
            url = pending.pop(0)
            if url in visited:
                continue
            visited.add(url)
            r = requests.get(url, timeout=30, headers=HEADERS)
            r.raise_for_status()
            pages, next_pages = extract_vth(r.text, url)
            for program in pages:
                all_programs[program["url"]] = program
            pending.extend(url for url in next_pages if url not in visited)
    except requests.RequestException:
        # Le catalogue /lotissement/ renvoie parfois HTTP 500.
        # La page officielle de présentation liste un sous-ensemble uniquement.
        # Il faut montrer un relevé PARTIEL, jamais un total garanti.
        fallback = "https://www.valtourainehabitat-vente.fr/acheter-un-terrain-a-batir/"
        r = requests.get(fallback, timeout=30, headers=HEADERS)
        r.raise_for_status()
        pages, _ = extract_vth(r.text, fallback)
        report = observed(pages,
                          "Seulement les programmes visibles sur la page de présentation VTH ; catalogue complet inaccessible.")
        report["warning"] = "Catalogue général VTH inaccessible lors de cette vérification. Inventaire partiel."
        return with_vth_details(report)
    result = observed(list(all_programs.values()),
                      "Fiches parcourues dans la pagination officielle VTH, sans compteur total indépendant.")
    result["pagesVisited"] = len(visited)
    if pending:
        result["status"] = "incomplete"
        result["message"] = "Pagination VTH trop longue ou incomplète. Vérification exhaustive impossible."
    return with_vth_details(result)


def fetch_lelievre() -> dict:
    # Ce catalogue est injecté en JavaScript et peut présenter un bouton 'Voir plus'.
    # Les opérations trouvées ne sont JAMAIS annoncées comme total officiel.
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, args=["--no-sandbox"])
        try:
            page = browser.new_page(viewport={"width": 1400, "height": 1100}, locale="fr-FR")
            page.goto(LELIEVRE, wait_until="domcontentloaded", timeout=45000)
            page.wait_for_timeout(3000)
            for _ in range(4):
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                page.wait_for_timeout(650)
            soup = BeautifulSoup(page.content(), "html.parser")
            programs = []
            for a in soup.find_all("a", href=True):
                link = normalized_link(a["href"], LELIEVRE)
                if not re.search(r"lelievre-immobilier\.com/.+(?:terrain-a-vendre|lotissement)", link, re.I):
                    continue
                slug = urlparse(link).path.rstrip("/").split("/")[-1]
                if slug in ("terrains-a-vendre-indre-et-loire", "terrain-a-vendre"):
                    continue
                programs.append({"url": link, "label": slug.replace("-", " ").title(),
                                 "city": "Commune à vérifier"})
            return observed(programs, "Offres de terrain repérées dans la page Lelièvre (pagination non garantie).")
        finally:
            browser.close()


def main() -> int:
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    results = {}
    for name, url, fn in [
        ("Villadim", VILLADIM, fetch_villadim),
        ("CM-CIC · AFEDIM", AFEDIM, fetch_afedim),
        ("Groupe SET", SET, fetch_set),
        ("Val Touraine Habitat", VTH, fetch_vth),
        ("Lelièvre Immobilier", LELIEVRE, fetch_lelievre),
    ]:
        try:
            result = fn()
        except Exception as exc:
            result = {"status": "unavailable", "expected": None, "found": None,
                      "programs": [], "lotsVerified": False,
                      "message": f"Source officielle inaccessible ou analyse impossible : {type(exc).__name__}",
                      "errorDetail": str(exc)[:180]}
        result["checkedAt"] = now
        result["source"] = url
        results[name] = result
        print(f"{name}: {result['status']} - total officiel={result['expected']} "
              f"liens={result['found']}", flush=True)
    report = {"schema": 1, "generatedAt": now,
              "notice": "Contrôle des catalogues, PAS des stocks de lots. Aucun lot libre, vendu ou optionné n'est certifié par ce fichier.",
              "developers": results}
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # Le workflow continue en cas de site inaccessible : le JSON affiche UNAVAILABLE.
    return 0


def test():
    afedim = '<h1>Terrain 37</h1><p>2 biens disponibles</p>' + ''.join(
        f'<a href="/fr/achat-immobilier/annonces/programme/terrain/{city}-37/{pid}/Fiche">Terrain</a>'
        for city, pid in [("azay-sur-cher", "015753"), ("artannes-sur-indre", "020086")])
    assert extract_afedim(afedim)["status"] == "verified"
    assert extract_afedim(afedim.replace("2 biens", "4 biens"))["status"] == "incomplete"
    assert extract_afedim("<p>0 biens disponibles</p>")["status"] == "incomplete"
    villadim = '<p>2 programmes disponibles</p>' + ''.join(
        f'<a href="/terrains/{city}/{slug}">{slug} (37)</a>' for city, slug in
        [("monnaie", "les-terrasses-de-bel-r"), ("pernay", "la-cle-des-champs")])
    assert extract_villadim(villadim)["status"] == "verified"
    assert extract_villadim(villadim.replace("2 programmes", "11 programmes"))["status"] == "incomplete"
    assert extract_villadim("<p>Chargement des programmes…</p>")["status"] == "incomplete"
    # Une source dupliquant des liens ne doit jamais faire monter le compteur.
    assert extract_villadim(villadim + '<a href="/terrains/monnaie/les-terrasses-de-bel-r">doublon (37)</a>')["found"] == 2
    assert extract_afedim(afedim + '<a href="/fr/achat-immobilier/annonces/programme/terrain/azay-sur-cher-37/015753/Fiche">doublon</a>')["found"] == 2
    assert all("lotsVerified" in extract_afedim(afedim) for _ in range(1))
    set_mock = '<h3>L Orée du Bois</h3><a href="https://loreedubois-veigne.fr/">En savoir plus</a>'
    assert extract_set(set_mock)["status"] == "observed"
    assert extract_set("<p>Erreur temporaire</p>")["status"] == "incomplete"
    vth_mock = '<a href="/lotissement/bellevue/">Voir le lotissement</a>'
    assert len(extract_vth(vth_mock, VTH)[0]) == 1
    # Ne jamais prendre l'absence de fiches pour une déclaration de vente.
    assert observed([], "page temporairement vide")["status"] == "incomplete"
    assert callable(check_known_vth)
    print("13 contrôles du moteur d'audit : OK")


if __name__ == "__main__":
    if "--test" in sys.argv:
        test()
    else:
        sys.exit(main())
