import { bootstrapApplication } from "@angular/platform-browser";
import { inject, provideAppInitializer } from "@angular/core";
import { provideRouter, Routes } from "@angular/router";
import { App } from "./app/app";
import { Store } from "./app/core/store";
import { Dashboard } from "./app/pages/dashboard";
import { Finance } from "./app/pages/finance";
import { Shopping } from "./app/pages/shopping";
import { Tasks } from "./app/pages/tasks";
import { Settings } from "./app/pages/settings";
import { Admin } from "./app/pages/admin";
const routes: Routes = [
  { path: "", component: Dashboard },
  { path: "per-me", component: Dashboard, data: { personal: true } },
  { path: "spese", component: Finance },
  { path: "lista", component: Shopping },
  { path: "mansioni", component: Tasks },
  { path: "impostazioni", component: Settings },
  { path: "admin", component: Admin },
  { path: "**", redirectTo: "" },
];
bootstrapApplication(App, {
  providers: [
    provideRouter(routes),
    provideAppInitializer(() => inject(Store).init()),
  ],
}).catch(console.error);
