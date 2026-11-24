# Partie A — Code-first

## Étape 1 — Créer et lancer le service

1. Quelle annotation fait de `EmpruntService` un service web ? Quelle annotation fait de `LivreIndisponibleException` une erreur déclarée dans le contrat ?
   - `@WebService` sur `EmpruntService` (serviceName, portName, targetNamespace).
   - `@WebFault(name = "LivreIndisponible", targetNamespace = "http://supnum.mr/bib")` sur `LivreIndisponibleException`.

2. Pourquoi les classes de données doivent-elles avoir un constructeur vide ?
   - JAXB a besoin d'un constructeur sans argument pour instancier les objets lors de la désérialisation XML, puis il remplit les champs publics (`numero`, `dateRetour`, `isbn`, `retourPrevu`). Sans ce constructeur, le mapping JAXB échoue.

## Étape 2 — Lire le WSDL généré

1. URL du service ? Deux opérations et leurs messages d'entrée/sortie ?
   - URL : `http://localhost:8080/bib/EmpruntService` (WSDL : `?wsdl`).
   - `emprunterLivre` : entrée `emprunterLivre`, sortie `emprunterLivreResponse`, fault `LivreIndisponibleException`.
   - `rendreLivre` : entrée `rendreLivre`, sortie `rendreLivreResponse`.

2. Quel style (document ou rpc) et quel use (literal ou encoded) ? SOAP 1.1 ou 1.2 ?
   - `style="document"`, `use="literal"`, transport HTTP. SOAP 1.1 (namespace `http://schemas.xmlsoap.org/wsdl/soap/`).

3. Quel type XSD a été généré pour `isbn` et pour `dateRetour` ? La règle de format de l'ISBN apparaît-elle ?
   - `isbn` : `xs:string` ; `dateRetour` : `xs:string` (et non `xs:date`).
   - La règle de format de l'ISBN (regex Java) n'apparaît pas dans le XSD généré. C'est une limite du code-first.

4. Dans la requête SoapUI, les paramètres `idAdherent` et `isbn` portent-ils un préfixe d'espace de noms ?
   - Non : les éléments enfants du wrapper `emprunterLivre` sont non qualifiés (pas de `elementFormDefault="qualified"`). Donc `<idAdherent>` et `<isbn>` sans préfixe.

5. Dans l'onglet Raw, où voyez-vous HTTP, XML, SOAP ? Quel rôle joue chacun, et quel rôle joue le WSDL ?
   - HTTP = transport (POST, en-têtes `Content-Type`, `SOAPAction`).
   - XML = syntaxe du message.
   - SOAP = convention d'enveloppe (`Envelope`, `Header`, `Body`, `Fault`) et règles de sérialisation.
   - WSDL = contrat du service : opérations, messages, types, binding (style/use) et adresse. Il décrit, il ne transporte pas les données.

## Étape 3 — Envoyer des messages SOAP

1. Pourquoi la requête utilise-t-elle POST, même pour lire une information ?
   - SOAP transporte le message dans le corps HTTP et invoque une opération ; HTTP GET ne convient pas (pas de corps structuré, cache, limites d'URL). SOAP est donc toujours en POST (sauf SOAP 1.2 GET optionnel pour des requêtes idempotentes).

2. Le bloc `jeton` du Header a-t-il empêché le traitement ? Pourquoi ?
   - Non. L'en-tête n'a pas `mustUnderstand="1"` et le service ne le déclare pas obligatoire : Metro l'ignore silencieusement (header optionnel).

3. Que répond le service au message SOAP 1.2 ? Expliquez avec le binding lu à l'étape 2.
   - En `text/xml` avec enveloppe 1.2 : HTTP 500, Fault `VersionMismatch` ("Attente de Envelope ... soap/envelope, mais ... soap-envelope obtenu"), car le binding est SOAP 1.1 uniquement.
   - En `application/soap+xml` : HTTP 415 Unsupported Media Type (le binding n'accepte que `text/xml`).

4. Listez les cinq erreurs du dernier message et leur correction. Certaines ne déclenchent aucune erreur côté serveur : lesquelles, et pourquoi ?
   1. `<soap:envelope>` (minuscule) → `<soap:Envelope>`.
   2. espace de noms `http://supnum.mr/bibliotheque` → `http://supnum.mr/bib`.
   3. `<bib:numero>` → `<numeroEmprunt>` (nom du paramètre).
   4. `</rendreLivre>` sans préfixe → `</bib:rendreLivre>`.
   5. `<soap:Header>` placé après `<soap:Body>` → doit être avant le Body.
   - Sans effet serveur : l'ordre Header/Body est toléré (Metro accepte quand même), et le jeton inconnu est ignoré (header optionnel). Avant l'ajout de la validation (étape 4), le mauvais nom `numero` produisait `null` et renvoyait `0` sans erreur.

## Étape 4 — Provoquer et construire des Faults

1. Pour les cas 1 et 2, quels faultcode et faultstring recevez-vous, et quel code HTTP ?
   - Cas 1 (ISBN `978-2-10`) : HTTP 500, faultcode `S:Client`, faultstring "ISBN mal formé : 978-2-10".
   - Cas 2 (livre déjà emprunté) : HTTP 500, faultcode `S:Server`, faultstring "Livre indisponible".

2. Dans le cas 2, que contient `<detail>` ? Où sa structure est-elle décrite dans le WSDL ?
   - `<detail>` contient l'élément `LivreIndisponible` (namespace `http://supnum.mr/bib`) avec `isbn` et `retourPrevu`. Sa structure est décrite dans le XSD du WSDL (type `livreIndisponibleInfo` / élément `LivreIndisponible`) et dans le message `LivreIndisponibleException`.

3. Quel code JAX-WS a-t-il choisi pour le livre indisponible ? Est-ce une erreur du client ou du serveur ? Qu'auriez-vous choisi ?
   - JAX-WS a choisi `Server` (défaut pour une exception applicative déclarée avec `@WebFault`).
   - Sémantiquement c'est plutôt une erreur du client (le client demande un livre indisponible). J'aurais choisi `Client` (ou un code applicatif `LivreIndisponible`).

4. Que se passe-t-il avec `mustUnderstand="1"` ? Et avec `"0"` ?
   - `mustUnderstand="1"` : Fault `MustUnderstand` (HTTP 500), car le service ne comprend pas l'en-tête `jeton`.
   - `mustUnderstand="0"` : l'en-tête est ignoré, la requête est traitée normalement (HTTP 200).

5. Quelle différence voyez-vous entre votre Fault 1.2 et les Faults 1.1 reçues ?
   - SOAP 1.2 : `<soap:Code>/<soap:Value>` (ex. `soap:Sender`), `<soap:Reason>/<soap:Text xml:lang="fr">`, `<soap:Detail>` ; code HTTP 500.
   - SOAP 1.1 : `<faultcode>`, `<faultstring>`, `<detail>`.
   - Les deux renvoient HTTP 500. SOAP 1.2 distingue Sender/Receiver et permet plusieurs textes localisés.

# Partie B — Contract-first

## Étape 5 — Générer le code depuis le WSDL

1. Dans ce WSDL, retrouvez l'opération, son message de fault et l'action SOAP à envoyer.
   - Opération : `emprunterLivre` dans le `portType` `EmpruntPortType`.
   - Fault : `LivreIndisponible` (message `LivreIndisponibleFault`, élément `bib:LivreIndisponible`).
   - Action SOAP : `http://supnum.mr/bib/emprunterLivre` (`soap:operation soapAction="..."`).

2. Quelle est la signature de `emprunterLivre` dans `EmpruntPortType` ?
   - `EmpruntType emprunterLivre(String idAdherent, String isbn) throws LivreIndisponibleFault;`
   - Annotations générées : `@WebMethod(action = "http://supnum.mr/bib/emprunterLivre")`, `@WebResult(name = "emprunt")`, `@RequestWrapper(...EmprunterLivre)` et `@ResponseWrapper(...EmprunterLivreResponse)`.

3. Quel type Java a été généré pour `dateRetour` ? Comparez avec la partie A.
   - `javax.xml.datatype.XMLGregorianCalendar`, avec `@XmlSchemaType(name = "date")` (le WSDL déclare `xs:date`).
   - Partie A : `String` (le WSDL code-first déclarait `xs:string`). Le contrat B est donc plus précis.

4. Quel est le nom de la classe d'exception générée, et d'où vient ce nom dans le WSDL ?
   - `mr.supnum.bib.contrat.LivreIndisponibleFault` (`@WebFault(name = "LivreIndisponible", targetNamespace = "http://supnum.mr/bib")`).
   - Il vient du `wsdl:fault name="LivreIndisponible"` : `LivreIndisponible` devient le bean du détail, et l'exception prend le suffixe `Fault` pour éviter le conflit de noms.

## Étape 6 — Implémenter et comparer

1. Le WSDL publié est-il celui que vous avez fourni ? Contient-il le motif de l'ISBN et le type `xs:date` ?
   - Oui : avec `wsdlLocation = "wsdl/EmpruntService.wsdl"`, Metro publie exactement le contrat fourni (`GET ?wsdl` → HTTP 200).
   - On y retrouve `IsbnType` avec le motif `97[89]-\d-\d{2,5}-\d{2,6}-\d`, et `dateRetour` / `retourPrevu` en `xs:date`.

2. Avez-vous dû modifier `01-emprunter.xml` pour qu'il fonctionne ici ? Pourquoi ?
   - Oui. Le schéma du contrat B a `elementFormDefault="qualified"` : les enfants du wrapper doivent être qualifiés (`<bib:idAdherent>`, `<bib:isbn>`). Le XSD généré en partie A laissait les enfants non qualifiés.
   - Sans préfixe (et avant l'ajout de `@SchemaValidation` à l'étape 6.5), JAXB reçoit `isbn = null` et le service répond une Fault `S:Server` « Cannot invoke "String.equals(Object)" because "isbn" is null ». Avec `@SchemaValidation`, la requête est rejetée plus tôt par la validation du schéma.
   - Variantes prêtes : `xml/10-emprunter-contractfirst.xml`, `xml/11-emprunter-contractfirst-fault.xml`, `xml/12-emprunter-contractfirst-invalide.xml`.

3. Sans `@SchemaValidation`, l'ISBN `978-2-10` est-il refusé ? Et avec ? Quelle Fault obtenez-vous ?
   - Sans : non, HTTP 200 avec un emprunt `E-2026-0812` ; l'implémentation ne teste que l'égalité avec `978-2-10-074444-4`, le motif du WSDL n'est pas vérifié à l'exécution.
   - Avec : HTTP 500, faultcode `S:Client`, faultstring `org.xml.sax.SAXParseException; cvc-pattern-valid: Value '978-2-10' is not facet-valid with respect to pattern '97[89]-\d-\d{2,5}-\d{2,6}-\d' for type 'IsbnType'.` (pas de `<detail>`).

4. Retirez `wsdlLocation` de l'annotation et redémarrez : qu'est-ce qui change dans le WSDL publié ?
   - Metro régénère le WSDL depuis les annotations/classes au lieu de servir le contrat.
   - `isbn` devient `xs:string` : le `simpleType IsbnType` et son motif disparaissent.
   - `dateRetour` et `retourPrevu` restent `xs:date` grâce à `@XmlSchemaType(name = "date")`.
   - Le WSDL importe son schéma via `?xsd=1` et expose des actions WS-Addressing ; le contrat n'est plus garanti.

## Conclusion — Choisir et rendre

1. Citez deux différences que vous avez observées entre le WSDL de la partie A et celui de la partie B.
   - Types : partie A `isbn`/`dateRetour` en `xs:string` sans règle ; partie B `IsbnType` avec motif et `dateRetour`/`retourPrevu` en `xs:date`.
   - Forme : partie B a `elementFormDefault="qualified"` (paramètres qualifiés) et une `soapAction` explicite ; partie A laisse les enfants non qualifiés. La partie B ne déclare que `emprunterLivre`, la partie A expose aussi `rendreLivre`.

2. Si on renomme le champ `dateRetour` dans le code, quel projet voit son contrat changer ? Quel risque pour les clients ?
   - Partie A (code-first) : le WSDL est généré depuis le code, donc renommer le champ change le contrat publié, silencieusement.
   - Partie B (contract-first) : le WSDL ne change pas tant que le contrat n'est pas modifié ; le code doit rester aligné sur le contrat.
   - Risque : rupture de compatibilité (élément XML renommé), erreurs de désérialisation ou champs vides chez les clients existants.

3. Quelle approche choisiriez-vous pour le service Paiement, appelé par Scolarité (.NET), Bibliothèque (PHP) et le portail (Node.js) ? Et pour un prototype interne de deux jours ?
   - Paiement : contract-first. Plusieurs équipes et langages partagent le même contrat ; le WSDL versionné sert de référence et chaque client génère ses stubs depuis ce contrat.
   - Prototype interne de deux jours : code-first, plus rapide ; on fige le WSDL avant toute mise en production.

## Vérifications effectuées

- État final du projet : `wsdlLocation` restauré + `@SchemaValidation` ajouté (étape 6.5).
- `mvn clean compile` : BUILD SUCCESS (11 sources : 2 écrites + 9 générées par wsimport).
- WSDL publié : `GET http://localhost:8080/bib/EmpruntService?wsdl` → HTTP 200, `IsbnType`, `xs:date`, `soapAction` présents.
- Emprunt valide (`xml/10-...`) → HTTP 200, `E-2026-0812`, `dateRetour 2026-12-08`.
- Livre indisponible (`xml/11-...`) → HTTP 500, `S:Server`, `<detail><LivreIndisponible>...`.
- ISBN invalide sans `@SchemaValidation` (`xml/12-...`) → HTTP 200.
- ISBN invalide avec `@SchemaValidation` → HTTP 500, `S:Client`, `cvc-pattern-valid`.


