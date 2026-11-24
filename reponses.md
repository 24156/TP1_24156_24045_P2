# TP1 — SOAP et WSDL — Réponses (Partie A : code-first)

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

## Remarque technique

Le lancement recommandé `mvn compile exec:java` fonctionne pour les réponses normales, mais les Faults échouaient initialement car les threads HTTP héritaient du classloader de Maven (implémentation JAXB invisible). Le `Serveur.java` a été adapté pour définir un `Executor` dont les threads utilisent le classloader de l'application, ce qui permet la sérialisation des Faults sans modifier le contrat.

## Rendu

- Projet Maven : `emprunt-codefirst/`
- Messages SOAP : dossier `xml/`
- Ce fichier : `reponses.md`
