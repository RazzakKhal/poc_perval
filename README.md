# POC – Intégration Friendly Captcha

Proof of concept d'intégration de [Friendly Captcha](https://friendlycaptcha.com) sur un formulaire de contact Angular + Spring Boot.

---

## Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────┐
│                        Navigateur                               │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              ContactFormComponent (Angular)              │   │
│  │                                                          │   │
│  │  [Nom]  [Email]  [Message]                               │   │
│  │                                                          │   │
│  │  ┌──────────────────────────────────┐                    │   │
│  │  │  Widget Friendly Captcha (SDK)   │                    │   │
│  │  │  → proof-of-work en arrière-plan │                    │   │
│  │  │  → émet frc:widget.complete      │──→ captchaToken    │   │
│  │  └──────────────────────────────────┘                    │   │
│  │                                                          │   │
│  │  [Envoyer]  ──────────────────────────────────────────── │   │
│  └──────────────────────────────────────────────────────────┘   │
│                          │ POST /api/contact                    │
│                          │ { name, email, message,             │
│                          │   captchaResponse: token }          │
└──────────────────────────┼──────────────────────────────────────┘
                           │
                     ┌─────▼──────────────────────────┐
                     │  Spring Boot – ContactController│
                     │                                │
                     │  FriendlyCaptchaService         │
                     │  ┌──────────────────────────┐  │
                     │  │ token vide ? → false      │  │
                     │  │ POST siteverify (proxy)   │──┼──→ Friendly Captcha API
                     │  │ success ? → 200 : 400     │  │    global.frcapi.com
                     │  └──────────────────────────┘  │
                     └────────────────────────────────┘
```

---

## Flux général

1. **Rendu du formulaire** — Angular affiche le composant `ContactFormComponent`. Le widget Friendly Captcha est initialisé dans le DOM via le SDK npm.

2. **Proof-of-work** — Le SDK exécute un calcul cryptographique côté navigateur (en arrière-plan, sans interaction utilisateur si `startMode: 'auto'`). Ce calcul prouve que le navigateur est légitime.

3. **Réception du token** — Une fois le calcul terminé, le SDK émet l'événement `frc:widget.complete`. Angular écoute cet événement et stocke le token dans `captchaToken`.

4. **Soumission** — L'utilisateur clique sur "Envoyer". Angular vérifie que `captchaToken` est présent (sinon affiche l'état `captcha-missing`) puis envoie une requête `POST /api/contact` avec les données du formulaire et le token.

5. **Validation serveur** — Spring Boot reçoit la requête. `FriendlyCaptchaService` appelle l'API `siteverify` de Friendly Captcha en transmettant le token et la sitekey. Cet appel transite par le proxy corporate si configuré.

6. **Réponse** — Friendly Captcha répond `{ success: true/false }`. Spring retourne `200 OK` si valide, `400 Bad Request` sinon. Angular affiche le message correspondant.

> **Principe de sécurité** : la validation est entièrement côté serveur. Un attaquant qui intercepterait le token ne peut pas contourner la vérification car elle passe par l'API key privée, jamais exposée côté Angular.

---

## Front-end Angular

### Structure

```
poc_captcha_front/
├── src/
│   ├── app/
│   │   ├── app.component.ts          # Shell — monte RouterOutlet
│   │   ├── app.config.ts             # provideHttpClient, provideRouter
│   │   ├── app.routes.ts             # routes Angular
│   │   ├── contact-form/
│   │   │   ├── contact-form.component.ts    # logique du formulaire
│   │   │   ├── contact-form.component.html  # template (Angular 17+ @if/@else)
│   │   │   └── contact-form.component.css
│   │   └── services/
│   │       └── contact.service.ts    # HttpClient vers le back-end
│   └── environments/
│       ├── environment.ts            # sitekey + backendUrl (dev)
│       └── environment.prod.ts       # sitekey + backendUrl (prod)
```

### Prérequis et installation

```bash
cd poc_captcha_front
npm install          # installe @friendlycaptcha/sdk et toutes les dépendances
npm start            # démarre sur http://localhost:4200
```

Le SDK Friendly Captcha est installé comme dépendance npm standard — **aucune balise `<script>` CDN n'est ajoutée dans `index.html`**.

### Configuration (environment.ts)

```typescript
export const environment = {
  production: false,
  friendlyCaptchaSiteKey: 'FCMV9RIFA4K6IUS1',   // clé publique, exposable
  backendUrl: 'http://localhost:8080/api/contact',
};
```

La `sitekey` est une clé **publique** — elle peut figurer dans le code Angular.  
L'`apikey` (clé privée) n'apparaît jamais côté front.

### Initialisation du widget

Le composant implémente `AfterViewInit` et `OnDestroy` pour gérer le cycle de vie du widget :

```typescript
ngAfterViewInit(): void {
  const sdk = new FriendlyCaptchaSDK();
  this.widget = sdk.createWidget({
    element: this.captchaContainer.nativeElement,  // <div #captchaContainer>
    sitekey: environment.friendlyCaptchaSiteKey,
    startMode: 'auto',    // voir section "Contrôler le démarrage"
  });

  // token disponible → le stocker
  this.captchaContainer.nativeElement.addEventListener('frc:widget.complete',
    (event: Event) => {
      this.captchaToken = (event as CustomEvent<{ response: string }>).detail.response;
    }
  );

  // token expiré ou erreur → l'invalider
  this.captchaContainer.nativeElement.addEventListener('frc:widget.error',
    () => { this.captchaToken = null; });
  this.captchaContainer.nativeElement.addEventListener('frc:widget.expire',
    () => { this.captchaToken = null; });
}

ngOnDestroy(): void {
  this.widget?.destroy();   // évite les fuites mémoire
}
```

### États du formulaire

| État | Déclencheur | Comportement affiché |
|---|---|---|
| `idle` | Initial | Formulaire actif |
| `loading` | Soumission en cours | Bouton désactivé, "Envoi en cours…" |
| `success` | `200 OK` du back | Message de confirmation, formulaire masqué |
| `error` | `400` ou erreur réseau | Message d'erreur contextuel, widget réinitialisé |
| `captcha-missing` | Soumission sans token | Message sous le widget, formulaire conservé |

---

## Back-end Spring Boot

### Structure

```
demo/
├── src/main/java/com/pervalpoc/demo/
│   ├── DemoApplication.java
│   ├── captcha/
│   │   ├── FriendlyCaptchaProperties.java    # @ConfigurationProperties
│   │   ├── FriendlyCaptchaService.java        # appel API siteverify
│   │   ├── FriendlyCaptchaVerifyRequest.java  # record (response, sitekey)
│   │   └── FriendlyCaptchaVerifyResponse.java # { success: boolean }
│   ├── contact/
│   │   ├── ContactController.java             # POST /api/contact
│   │   └── ContactRequest.java                # DTO (name, email, message, captchaResponse)
│   └── config/
│       └── WebConfig.java                     # CORS
└── src/main/resources/
    └── application.properties
```

### Prérequis et lancement

```bash
cd demo

# Avec la clé de test en variable d'environnement (recommandé)
export FRIENDLY_CAPTCHA_API_KEY=<votre_api_key>
./mvnw spring-boot:run

# Ou directement (la valeur de fallback dans application.properties sera utilisée)
./mvnw spring-boot:run
```

Le back-end démarre sur le port `8080`.

### Configuration (application.properties)

```properties
friendly-captcha.site-key=FCMV9RIFA4K6IUS1
friendly-captcha.api-key=${FRIENDLY_CAPTCHA_API_KEY:valeur_de_fallback_pour_le_poc}
friendly-captcha.verify-url=https://global.frcapi.com/api/v2/captcha/siteverify

# Proxy corporate (laisser vide si pas de proxy)
app.proxy.host=proxy.intranet-adsn.fr
app.proxy.port=8080
```

### Proxy corporate

Dans un environnement avec proxy réseau, les appels HTTP sortants vers `global.frcapi.com` doivent y transiter. `FriendlyCaptchaService` construit le `RestClient` de manière conditionnelle :

```java
if (!proxyHost.isBlank()) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
    this.restClient = RestClient.builder().requestFactory(factory).build();
} else {
    this.restClient = RestClient.create();
}
```

Pour désactiver le proxy (environnement sans contrainte réseau), supprimer ou vider `app.proxy.host` dans `application.properties`.

### CORS (WebConfig)

```java
registry.addMapping("/api/**")
        .allowedOrigins("http://localhost:4200", "https://razzakkhal.github.io")
        .allowedMethods("POST")
        .allowedHeaders("*");
```

Seule la méthode `POST` est autorisée. Adapter `allowedOrigins` selon l'environnement cible.

### Appel à l'API Friendly Captcha

```
POST https://global.frcapi.com/api/v2/captcha/siteverify
X-API-Key: <api_key_privée>
Content-Type: application/json

{
  "response": "<token_reçu_depuis_angular>",
  "sitekey": "FCMV9RIFA4K6IUS1"
}
```

Réponse attendue : `{ "success": true }` ou `{ "success": false }`.

En cas d'erreur réseau ou d'exception, le service retourne `false` (**fail closed** — la soumission est rejetée par défaut).

### Codes HTTP retournés à Angular

| Cas | Code |
|---|---|
| Captcha valide | `200 OK` |
| Captcha absent ou invalide | `400 Bad Request` |
| Erreur serveur non maîtrisée | `500 Internal Server Error` |

---

## Contrôler le démarrage automatique du captcha

Par défaut, le widget est configuré avec `startMode: 'auto'` : le proof-of-work démarre **immédiatement** au rendu du composant, de manière transparente pour l'utilisateur. Le token est généralement prêt avant même que l'utilisateur ait fini de remplir le formulaire.

Pour modifier ce comportement, changer l'option `startMode` dans `contact-form.component.ts` :

```typescript
this.widget = sdk.createWidget({
  element: this.captchaContainer.nativeElement,
  sitekey: environment.friendlyCaptchaSiteKey,
  startMode: 'auto',   // ← modifier ici
});
```

### Valeurs disponibles

| `startMode` | Comportement |
|---|---|
| `'auto'` | **(défaut POC)** Démarre automatiquement à l'initialisation du widget. Transparent pour l'utilisateur, token prêt au moment de la soumission. |
| `'focus'` | Démarre quand l'utilisateur interagit pour la première fois avec la page (focus sur n'importe quel élément). Bon compromis : retarde le calcul sans bloquer la soumission. |
| `'none'` | Ne démarre **jamais** automatiquement. L'utilisateur doit cliquer sur le widget pour lancer le calcul. Le bouton "Envoyer" sera bloqué jusqu'à ce que le token soit généré. |

### Recommandation

- **`'auto'`** : à privilégier en production pour maximiser le taux de succès. Le calcul se fait en tâche de fond sans affecter l'UX.
- **`'focus'`** : si l'on veut éviter tout calcul sur les pages non consultées activement (économie de ressources côté client).
- **`'none'`** : si l'on veut que l'action captcha soit un geste explicite de l'utilisateur (cas rares, réduit la fluidité UX).

### Exemple avec `startMode: 'none'`

Avec `'none'`, le widget affiche un bouton "Vérifier". L'utilisateur doit cliquer dessus avant de soumettre. Le formulaire reste fonctionnellement identique côté Angular — l'état `captcha-missing` gérera le cas où l'utilisateur soumet sans avoir cliqué.

```typescript
this.widget = sdk.createWidget({
  element: this.captchaContainer.nativeElement,
  sitekey: environment.friendlyCaptchaSiteKey,
  startMode: 'none',
});
```

---

## Docker (back-end)

```bash
cd demo
docker build -t poc-captcha-back .
docker run -p 8080:8080 \
  -e FRIENDLY_CAPTCHA_API_KEY=<votre_api_key> \
  poc-captcha-back
```

Le `Dockerfile` utilise un build multi-stage : Maven compile le jar dans une image de build, seul le JRE est conservé dans l'image finale.

---

## Résumé des clés

| Clé | Visibilité | Emplacement |
|---|---|---|
| `sitekey` (`FCMV9RIFA4K6IUS1`) | Publique — exposable dans Angular | `environment.ts`, `application.properties` |
| `apikey` | **Privée — jamais dans le code Angular** | Variable d'environnement `FRIENDLY_CAPTCHA_API_KEY`, fallback dans `application.properties` pour le POC uniquement |
