function rgb(hex: string) {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
}
function blend(hex: string, toward: string, n: number) {
  const a = rgb(hex),
    b = rgb(toward);
  return (
    "#" +
    a
      .map((v, i) =>
        Math.round(v * (1 - n) + b[i] * n)
          .toString(16)
          .padStart(2, "0"),
      )
      .join("")
  );
}
function luminance(hex: string) {
  return rgb(hex)
    .map((v) => v / 255)
    .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
    .reduce((s, v, i) => s + v * [0.2126, 0.7152, 0.0722][i], 0);
}
function contrast(a: string, b: string) {
  const x = luminance(a),
    y = luminance(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}
export function applyTheme(color: string) {
  if (!/^#[a-f0-9]{6}$/i.test(color)) color = "#3d59c9";
  const soft = blend(color, "#ffffff", 0.91);
  let text = color;
  for (let i = 0; i < 30 && contrast(text, soft) < 4.5; i++)
    text = blend(text, "#000000", 0.14);
  const css = document.documentElement.style;
  css.setProperty("--brand", color);
  css.setProperty(
    "--brand-ink",
    contrast(color, "#ffffff") >= 4.5 ? "#ffffff" : "#000000",
  );
  css.setProperty("--soft", soft);
  css.setProperty("--accent", text);
  css.setProperty("--hero", blend(color, "#ffffff", 0.84));
}
