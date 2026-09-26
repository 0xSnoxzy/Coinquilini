import { Injectable, computed, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { Payload, Snapshot } from "./models";
import { today, plusDays } from "./dates";
import fixtures from "./fixtures.json";

/** SOLO PRESENTAZIONE: nessuna API, password, persistenza o regola economica.
 * Sostituire questo servizio gradualmente seguendo docs/02-ROADMAP-E-COMMIT.md.
 */
@Injectable({ providedIn: "root" })
export class Store {
  readonly data = signal<Snapshot | null>(null);
  readonly busy = signal(false);
  readonly ready = signal(false);
  readonly message = signal("");
  readonly error = signal("");
  readonly mode = signal<"demo">("demo");
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
    this.ready.set(true);
  }
  private fixture(name: string): Snapshot {
    let json = JSON.stringify(fixtures);
    for (let i = 0; i < 4; i++)
      json = json.replaceAll(`__DAY_${i}__`, plusDays(today(), i));
    return (JSON.parse(json) as Record<string, Snapshot>)[name];
  }
  preview(name: string) {
    this.scene = [
      "Antonio",
      "David",
      "Elisa",
      "Giovanna",
      "Admin",
      "Nuovo",
    ].includes(name)
      ? name
      : "Antonio";
    this.data.set(this.fixture(this.scene));
    this.error.set("");
    void this.router.navigateByUrl(this.scene === "Admin" ? "/admin" : "/");
  }
  async refresh() {
    this.preview(this.scene);
    this.notify("Anteprima ripristinata.");
  }
  async auth(
    _register: boolean,
    _name: string,
    _email: string,
    _password: string,
  ) {
    this.notify(
      "Modulo grafico: usa i pulsanti dei profili di esempio per esplorare.",
    );
    return false;
  }
  async logout() {
    this.data.set(null);
    this.error.set("");
    void this.router.navigateByUrl("/");
  }
  async send(path: string, p: Payload, _message = "") {
    this.error.set("");
    const state = this.data();
    if (!state) return false;
    if (path === "profile/save") {
      const user = {
        ...state.user,
        name: p["name"],
        color: p["color"],
        photo: p["photo"],
      };
      this.data.set({
        ...state,
        user,
        members: state.members.map((m) =>
          m.id === user.id ? { ...m, ...user } : m,
        ),
      });
      this.notify("Aspetto aggiornato nell’anteprima; non viene salvato.");
      return true;
    }
    if (path === "houses/save") {
      this.data.set({
        ...state,
        house: {
          ...state.house!,
          name: p["name"],
          emoji: p["emoji"],
          photo: p["photo"],
        },
      });
      this.notify("Aspetto della casa aggiornato solo nell’anteprima.");
      return true;
    }
    if (path === "shopping/toggle") {
      this.data.set({
        ...state,
        items: state.items.map((i) =>
          i.id === p["id"] ? { ...i, bought: !!p["bought"] } : i,
        ),
      });
      return true; // Stato visivo della checkbox, per aprire anche il modulo acquisto.
    }
    if (path === "houses/create" || path === "houses/join") {
      const name = state.user.name;
      this.preview(path === "houses/create" ? "Antonio" : "David");
      const s = this.data()!;
      this.data.set({
        ...s,
        user: { ...s.user, name },
        house: { ...s.house!, name: p["name"] || "Casa di esempio" },
      });
      this.notify(
        "Casa di esempio: nessun account o invito reale è stato creato.",
      );
      return true;
    }
    if (path === "houses/leave") {
      this.preview("Nuovo");
      return true;
    }
    this.notify(
      "Anteprima: questa modifica non viene salvata.",
    );
    return true;
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
