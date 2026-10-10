-- =====================================================================
--  ATURAN BAWAAN MONOPOLI (basis)
--  =====================================================================
--
--  File ini adalah MOD BASIS (id: "inti", prioritas 0, selalu aktif).
--  Ia mengisi nilai aturan baku lewat M.setRule. Mod user di folder
--  mods/ menumpuk di atasnya: prioritas lebih tinggi menang.
--
--  Kunci yang bisa diubah (M.setRule):
--    starting_cash, go_salary, jail_fine, jail_max_turns, max_houses,
--    three_doubles_to_jail, monopoly_doubles_rent, income_tax, luxury_tax
--
--  Cara custom tanpa sentuh kode: buat folder mods/<id>/ dengan mod.json
--  + scripts/rules.lua. Lihat docs/cetak-biru/ untuk kontrak lengkap.
-- =====================================================================

M.setRule("starting_cash", 1500)
M.setRule("go_salary", 200)
M.setRule("jail_fine", 50)
M.setRule("jail_max_turns", 3)
M.setRule("max_houses", 5)              -- 5 = hotel
M.setRule("three_doubles_to_jail", true)
M.setRule("monopoly_doubles_rent", true)
M.setRule("income_tax", 200)
M.setRule("luxury_tax", 100)

-- Catatan: sewa stasiun (25/50/100/200) & utilitas (4x/10x dadu) adalah
-- RUMUS di engine, bukan angka di sini. Mod yang mau mengubahnya memakai
-- event "land" + M.takeMoney (contoh ada di docs/cetak-biru/10).
