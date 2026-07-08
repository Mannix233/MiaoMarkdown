# Render paper before phone preview

Miao Markdown treats the Paperang P1's 384-dot print surface as the source of truth. Markdown, tables, and formulas are rendered into a 384-dot paper image first; the phone preview only shows a scaled copy of that image. This avoids mixing phone WebView/CSS coordinates with printer dots, which can make wide formulas or tables appear correct on screen but print only a clipped half.
