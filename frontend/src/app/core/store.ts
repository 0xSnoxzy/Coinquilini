import { Injectable, computed, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { Payload, Snapshot } from "./models";
import { today, plusDays } from "./dates";
import fixtures from "./fixtures.json";

/** Autenticazione, profilo e casa reali; anteprime separate dai dati della sessione. */
@Injectable({ providedIn: "root" })
export class Store {
  readonly data = signal<Snapshot | null>(null);
  readonly busy = signal(false);
  readonly ready = signal(false);
  readonly message = signal("");
  readonly error = signal("");
  readonly mode = signal<"demo" | "live">("demo");
  readonly user = computed(() => this.data()?.user || null);
  readonly house = computed(() => this.data()?.house || null);
  readonly members = computed(() => this.data()?.members || []);
  readonly activeMembers = computed(() =>
    this.members().filter((m) => m.memberActive),
  );
  readonly owner = computed(() =>
    this.members().some(
      (m) =>
        m.id === this.user()?.id &&
        m.memberActive &&
        m.membershipRole === "OWNER",
    ),
  );
  private router = inject(Router);
  private scene = "Antonio";
  private timer: ReturnType<typeof setTimeout> | undefined;
  async init() {
    this.busy.set(true);
    try {
      await this.loadSnapshot();
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) this.fail(e);
    } finally {
      this.busy.set(false);
      this.ready.set(true);
    }
  }
  private fixture(name: string): Snapshot {
    let json = JSON.stringify(fixtures);
    for (let i = 0; i < 4; i++)
      json = json.replaceAll(`__DAY_${i}__`, plusDays(today(), i));
    return (JSON.parse(json) as Record<string, Snapshot>)[name];
  }
  preview(name: string) {
    if (this.mode() === "live" && this.user()) return;
    this.scene = ["Antonio", "David", "Elisa", "Giovanna", "Admin", "Nuovo"].includes(name)
      ? name : "Antonio";
    this.mode.set("demo");
    this.data.set(this.fixture(this.scene));
    this.error.set("");
    void this.router.navigateByUrl(this.scene === "Admin" ? "/admin" : "/");
  }
  async refresh() {
    if (this.busy()) return;
    if (this.mode() === "demo") {
      this.preview(this.scene);
      this.notify("Dati di esempio ripristinati.");
      return;
    }
    this.busy.set(true);
    this.error.set("");
    try {
      await this.loadSnapshot();
      this.notify("Dati aggiornati.");
    } catch (e) {
      this.fail(e);
    } finally {
      this.busy.set(false);
    }
  }
  async auth(register: boolean, name: string, email: string, password: string) {
    if (this.busy()) return false;
    this.busy.set(true);
    this.error.set("");
    let authenticated = false;
    try {
      await this.request(register ? "auth/register" : "auth/login", "POST",
        register ? { name, email, password } : { email, password });
      authenticated = true;
      await this.loadSnapshot();
      this.notify(register ? "Registrazione effettuata con successo!" : "Accesso effettuato!");
      void this.router.navigateByUrl("/");
      return true;
    } catch (e) {
      this.fail(authenticated
        ? new Error("Accesso effettuato, ma i dati non sono stati caricati. Ricarica la pagina.")
        : e);
      return false;
    } finally {
      this.busy.set(false);
    }
  }
  async logout() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    try {
      if (this.mode() === "live") await this.request("auth/logout", "POST");
      this.data.set(null);
      this.mode.set("demo");
      this.message.set("");
      void this.router.navigateByUrl("/");
    } catch (e) {
      this.fail(e);
    } finally {
      this.busy.set(false);
    }
  }
  async send(path: string, p: Payload, message = "") {
    if (this.busy()) return false;
    this.error.set("");
    const state = this.data();
    if (!state) return false;
    if (this.mode() === "live") {
      if (!["houses/create", "houses/join", "profile/save"].includes(path)) {
        this.error.set("Questa funzione non è ancora collegata al database.");
        return false;
      }
      this.busy.set(true);
      let saved = false;
      try {
        await this.request(path, path === "profile/save" ? "PUT" : "POST", p);
        saved = true;
        await this.loadSnapshot();
        this.notify(message || (path === "houses/create" ? "Casa creata!"
          : path === "houses/join" ? "Sei entrato nella casa!" : "Profilo salvato."));
        if (path.startsWith("houses/")) void this.router.navigateByUrl("/");
        return true;
      } catch (e) {
        this.fail(saved
          ? new Error("Operazione salvata. Impossibile aggiornare i dati: premi Aggiorna dati o ricarica la pagina.")
          : e);
        return false;
      } finally {
        this.busy.set(false);
      }
    }
    // Le anteprime non inviano scritture alle API reali.
    if (path === "profile/save") {
      const user = { ...state.user, ...p };
      this.data.set({ ...state, user,
        members: state.members.map((m) => m.id === user.id ? { ...m, ...user } : m) });
    } else if (path === "houses/save") {
      this.data.set({ ...state, house: { ...state.house!,
        name: p["name"], emoji: p["emoji"], photo: p["photo"] } });
    } else if (path === "shopping/toggle") {
      this.data.set({ ...state, items: state.items.map((i) =>
        i.id === p["id"] ? { ...i, bought: !!p["bought"] } : i) });
      return true;
    } else if (path === "houses/create" || path === "houses/join") {
      const name = state.user.name;
      this.preview(path === "houses/create" ? "Antonio" : "David");
      const demo = this.data()!;
      this.data.set({ ...demo, user: { ...demo.user, name },
        house: { ...demo.house!, name: p["name"] || "Casa di esempio" } });
    } else if (path === "houses/leave") {
      this.preview("Nuovo");
    }
    this.notify("Anteprima: questa modifica non viene salvata nel database.");
    return true;
  }
  private async loadSnapshot() {
    try {
      const snapshot = await this.request<Snapshot>("snapshot");
      this.data.set(snapshot);
      this.mode.set("live");
    } catch (e) {
      if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
        this.data.set(null);
        this.mode.set("demo");
      }
      throw e;
    }
  }
  private async request<T = unknown>(path: string, method = "GET", payload?: Payload): Promise<T> {
    let response: Response;
    try {
      response = await fetch("http://localhost:8080/api/" + path, {
        method,
        credentials: "include",
        ...(payload === undefined ? {} : {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        }),
      });
    } catch {
      throw new Error("Impossibile contattare il server backend sulla porta 8080.");
    }
    const body = await response.json();
    if (!response.ok) throw new ApiError(body.error || "Operazione non riuscita.", response.status);
    return body as T;
  }
  fail(e: unknown) {
    this.error.set(e instanceof Error ? e.message : "Immagine non valida.");
  }
  notify(value: string) {
    this.message.set(value);
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.message.set(""), 6500);
  }
  name(id: number) {
    return this.members().find((m) => m.id === id)?.name || "Ex coinquilino";
  }
  mine(id: number) {
    return this.user()?.id === id;
  }
  canEdit(authorId: number) {
    return this.mine(authorId) || this.owner();
  }
  euros(cents: number) {
    return new Intl.NumberFormat("it-IT", {
      style: "currency",
      currency: "EUR",
    }).format(cents / 100);
  }
  date(value: string) {
    return value === today()
      ? "Oggi"
      : new Intl.DateTimeFormat("it-IT", {
          day: "numeric",
          month: "short",
          year: "numeric",
        }).format(new Date(value + "T12:00:00"));
  }
  async photo(file: File): Promise<string> {
    if (
      !["image/png", "image/jpeg", "image/webp"].includes(file.type) ||
      file.size > 5000000
    )
      throw Error("Scegli PNG, JPEG o WebP sotto 5 MB.");
    const bitmap = await createImageBitmap(file);
    const canvas = document.createElement("canvas");
    const ratio = Math.min(1, 640 / Math.max(bitmap.width, bitmap.height));
    canvas.width = Math.round(bitmap.width * ratio);
    canvas.height = Math.round(bitmap.height * ratio);
    canvas
      .getContext("2d")!
      .drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    return canvas.toDataURL("image/jpeg", 0.82);
  }
}

class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}
