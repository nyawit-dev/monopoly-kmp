-- =====================================================================
--  CONTOH MOD: POT FREE PARKING
--  Mod ini adalah contoh hidup (bahan belajar), bukan bagian aturan
--  bawaan. Semua yang bisa dilakukan mod ada di sini: mengubah angka
--  aturan, mendengarkan event, dan melakukan aksi terlihat pemain.
--
--  Yang bisa diubah (M.setRule):
--    starting_cash, go_salary, jail_fine, jail_max_turns, max_houses,
--    three_doubles_to_jail, monopoly_doubles_rent, income_tax, luxury_tax
--
--  Event yang didengarkan (M.on) di file ini:
--    turn_start, land, pass_start, jail_enter, game_over
--
--  Semua kunci dan event di bawah sudah dicek terhadap kode engine
--  (RuleRegistry.kt & GameEvent.kt). Kunci/event tak dikenal hanya
--  menghasilkan peringatan di Log Mod, game tetap jalan.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. MENGUBAH ATURAN (dijalankan sekali saat mod dimuat)
--
-- M.setRule menimpa angka bawaan. Prioritas mod ini (100) lebih tinggi
-- dari mod basis (0), jadi nilai di bawah yang dipakai engine.
-- Nilai default ditulis di komentar supaya mudah dibandingkan.
-- ---------------------------------------------------------------------

M.setRule("starting_cash", 2000)  -- bawaan 1500: modal awal lebih besar
M.setRule("go_salary", 250)       -- bawaan 200: gaji START lebih besar
M.setRule("jail_fine", 75)        -- bawaan 50: denda penjara lebih mahal
M.setRule("max_houses", 5)        -- bawaan 5: 5 = hotel

-- M.getRule membaca nilai yang sedang aktif (berguna untuk pesan).
-- M.log menulis satu baris ke Log Mod (Menu MOD -> LOG MOD).
M.log("contoh-pot aktif: modal awal $" .. M.getRule("starting_cash") ..
      ", gaji START $" .. M.getRule("go_salary"))


-- ---------------------------------------------------------------------
-- 2. EVENT: turn_start (giliran pemain dimulai)
--
-- ctx yang bisa dibaca: player {name, money, position, in_jail},
--                       turn_number
-- ---------------------------------------------------------------------

M.on("turn_start", function(ctx)
  -- Hanya sapa di giliran pertama agar tidak berisik.
  if ctx.turn_number == 1 then
    M.message("Selamat bermain! Mod contoh Pot aktif.")
    M.log("Giliran 1: " .. ctx.player.name .. " mulai dengan $" ..
          ctx.player.money)
  end
end)


-- ---------------------------------------------------------------------
-- 3. EVENT: land (pemain mendarat di sebuah petak)
--
-- ctx yang bisa dibaca: player, tile {index, name, type, price,
--                       color_group, owner}, dice {d1, d2, total,
--                       is_double}
--
-- Di sini petak 20 (FREE_PARKING) dijadikan "pot": setiap pemain yang
-- mendarat mendapat uang hadiah. Ini contoh aksi imperatif (langsung
-- berlaku) + M.message (tulisan di bar pesan).
-- ---------------------------------------------------------------------

local POT_HADIAH = 500

M.on("land", function(ctx)
  -- tile selalu ada di event land, tapi kita tetap berjaga.
  if ctx.tile == nil then
    return
  end

  -- 3a. Pot Free Parking: hadiah uang.
  if ctx.tile.name == "FREE_PARKING" then
    M.message(ctx.player.name .. " memanen pot $" .. POT_HADIAH .. "!")
    M.giveMoney(ctx.player.name, POT_HADIAH)
    M.log("Pot dibayar ke " .. ctx.player.name .. " ($" .. POT_HADIAH ..
          ") dari petak " .. ctx.tile.index)
    return
  end

  -- 3b. Mendarat di petak pajak: pesan tambahan (tanpa mengubah uang).
  if ctx.tile.name == "Luxury Tax" then
    M.message(ctx.player.name .. ", pajak mewah! Coba hindari petak 38.")
  end

  -- 3c. Contoh membaca dadu: dua kembar membuat pesan khusus.
  if ctx.dice ~= nil and ctx.dice.is_double then
    M.log(ctx.player.name .. " mendarat di " .. ctx.tile.name ..
          " dengan dadu kembar " .. ctx.dice.d1 .. "-" .. ctx.dice.d2)
  end
end)


-- ---------------------------------------------------------------------
-- 4. EVENT: pass_start (pemain melewati atau mendarat di START)
--
-- ctx yang bisa dibaca: player, from, to
-- Contoh balikan tabel: money_delta (aditif) dan message (tampil di bar
-- pesan). Bedanya dengan M.giveMoney/M.message: balikan tabel ini
-- digabungkan engine dengan handler mod lain.
-- ---------------------------------------------------------------------

M.on("pass_start", function(ctx)
  local gaji = M.getRule("go_salary")
  return {
    money_delta = 100,               -- bonus tambahan di atas gaji bawaan
    message = ctx.player.name .. " melewati START (+$" .. gaji ..
              " gaji, +$100 bonus mod)"
  }
end)


-- ---------------------------------------------------------------------
-- 5. EVENT: jail_enter (pemain masuk penjara)
--
-- ctx yang bisa dibaca: player, reason ("go_to_jail_card" /
--                       "three_doubles")
-- Handler ini hanya memberi pendapat lewat balikan tabel.
-- ---------------------------------------------------------------------

M.on("jail_enter", function(ctx)
  if ctx.reason == "three_doubles" then
    return {
      message = "Tiga kembar! " .. ctx.player.name .. " ke penjara.",
      money_delta = -25              -- denda tambahan untuk empati
    }
  end

  if ctx.reason == "go_to_jail_card" then
    return {
      message = "Kartu ke penjara: " .. ctx.player.name .. " ditahan."
    }
  end
end)


-- ---------------------------------------------------------------------
-- 6. EVENT: game_over (permainan selesai)
--
-- ctx yang bisa dibaca: winner, net_worth
-- ---------------------------------------------------------------------

M.on("game_over", function(ctx)
  M.message("Permainan selesai. Pemenang: " .. tostring(ctx.winner) ..
            " dengan nilai kekayaan $" .. tostring(ctx.net_worth) .. ".")
  M.log("Game over: pemenang " .. tostring(ctx.winner))
end)
