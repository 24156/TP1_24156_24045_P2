# TP1 — SOAP et WSDL

**Binôme :** 24156 / 24045 (P2)

## Contenu du rendu

- `emprunt-codefirst/` — Partie A (code-first)
- `emprunt-contractfirst/` — Partie B (contract-first)
- `xml/` — messages SOAP des étapes 3 et 4, plus variantes contract-first
- `reponses.md` — réponses aux questions des parties A et B

## Prérequis

- JDK 17+
- Maven 3.9+
- Port 8080 libre (un seul service à la fois)

## Partie A — code-first

```bash
cd emprunt-codefirst
mvn clean compile exec:java
```

WSDL : http://localhost:8080/bib/EmpruntService?wsdl

## Partie B — contract-first

```bash
cd emprunt-contractfirst
mvn clean generate-sources
mvn compile exec:java
```

Le WSDL source est `src/main/resources/wsdl/EmpruntService.wsdl`. Les classes
`mr.supnum.bib.contrat.*` sont générées par `wsimport` dans
`target/generated-sources/wsimport/` (ne pas les modifier à la main).

WSDL publié : http://localhost:8080/bib/EmpruntService?wsdl

## Tests rapides (curl)

Partie A :

```bash
curl -i -X POST http://localhost:8080/bib/EmpruntService \
  -H 'Content-Type: text/xml; charset=utf-8' \
  -H 'SOAPAction: ""' \
  --data-binary @xml/01-emprunter.xml
```

Partie B (paramètres qualifiés `bib:`) :

```bash
curl -i -X POST http://localhost:8080/bib/EmpruntService \
  -H 'Content-Type: text/xml; charset=utf-8' \
  -H 'SOAPAction: "http://supnum.mr/bib/emprunterLivre"' \
  --data-binary @xml/10-emprunter-contractfirst.xml
```

Autres cas : `xml/06` à `xml/09` (Partie A), `xml/11` et `xml/12` (Partie B).
`xml/04-rendre-cinq-erreurs.xml` est volontairement invalide (exercice des cinq erreurs) ;
`xml/05-rendre-corrige.xml` est la version corrigée.
