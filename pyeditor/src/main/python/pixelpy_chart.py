"""Generador SVG liviano y reproducible para PixelPy."""

from html import escape
from pathlib import Path


def chart(rows, x, y, kind="line", title="Gráfico", output="grafico.svg"):
    values = list(rows)
    if not values:
        raise ValueError("No hay filas para graficar")
    if kind not in {"line", "bar", "scatter"}:
        raise ValueError("kind debe ser line, bar o scatter")
    if len(values) > 500:
        step = max(1, len(values) // 500)
        values = values[::step][:500]
    labels = [str(row[x]) for row in values]
    numbers = [float(str(row[y]).replace(",", ".")) for row in values]
    width, height = 900, 520
    left, top, right, bottom = 76, 60, 28, 76
    plot_w, plot_h = width - left - right, height - top - bottom
    minimum, maximum = min(numbers), max(numbers)
    spread = maximum - minimum or 1.0

    def px(index):
        return left + (plot_w * index / max(1, len(numbers) - 1))

    def py(value):
        return top + plot_h - (value - minimum) * plot_h / spread

    ink, blue, green, yellow, paper = "#202124", "#74B9FF", "#7DE39B", "#FFD83D", "#FFF7E6"
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        f'<rect width="100%" height="100%" fill="{paper}"/>',
        f'<text x="{left}" y="34" font-family="sans-serif" font-size="22" font-weight="900" fill="{ink}">{escape(title)}</text>',
        f'<path d="M {left} {top} V {top + plot_h} H {left + plot_w}" fill="none" stroke="{ink}" stroke-width="4"/>',
    ]
    if kind == "bar":
        bar_width = max(3, plot_w / max(1, len(numbers)) * 0.72)
        for index, value in enumerate(numbers):
            x_pos, y_pos = px(index), py(value)
            parts.append(
                f'<rect x="{x_pos - bar_width / 2:.1f}" y="{y_pos:.1f}" width="{bar_width:.1f}" '
                f'height="{top + plot_h - y_pos:.1f}" fill="{blue}" stroke="{ink}" stroke-width="2"/>'
            )
    else:
        points = " ".join(f"{px(i):.1f},{py(v):.1f}" for i, v in enumerate(numbers))
        if kind == "line":
            parts.append(f'<polyline points="{points}" fill="none" stroke="{blue}" stroke-width="5"/>')
        for index, value in enumerate(numbers):
            parts.append(f'<circle cx="{px(index):.1f}" cy="{py(value):.1f}" r="6" fill="{green}" stroke="{ink}" stroke-width="3"/>')
    label_step = max(1, len(labels) // 8)
    for index in range(0, len(labels), label_step):
        parts.append(
            f'<text x="{px(index):.1f}" y="{top + plot_h + 28}" text-anchor="middle" '
            f'font-family="sans-serif" font-size="13" font-weight="700" fill="{ink}">{escape(labels[index][:14])}</text>'
        )
    parts.extend([
        f'<text x="{left - 10}" y="{top + 8}" text-anchor="end" font-family="sans-serif" font-size="13" fill="{ink}">{maximum:g}</text>',
        f'<text x="{left - 10}" y="{top + plot_h}" text-anchor="end" font-family="sans-serif" font-size="13" fill="{ink}">{minimum:g}</text>',
        f'<rect x="{width - 150}" y="18" width="120" height="30" fill="{yellow}" stroke="{ink}" stroke-width="2"/>',
        f'<text x="{width - 90}" y="39" text-anchor="middle" font-family="sans-serif" font-size="12" font-weight="900" fill="{ink}">PIXELPY</text>',
        "</svg>",
    ])
    destination = Path(output)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("".join(parts), encoding="utf-8")
    return str(destination)
