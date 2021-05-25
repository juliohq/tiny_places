-- 
-- Dialog to register a new account
--
-- Author: Hj. Malthaner
-- Date: 2021/05/25
--

local cf = require("ui/component_factory")

-- UI element container for this UI
local container = cf.makeContainer()

local newAccountPopup = {}

local function createCallback(x, y, pressed)
  if not pressed then 
    print("Create account pressed!")
  end
end


local function init(mainUi)

  print("Loading new account popup")
  newAccountPopup.mainUi = mainUi
  
  local accountNameInput = cf.makeInput("Test", mainUi.uifont, 220, 110, 360, 32, nil)
  container:add(accountNameInput)

  local accountPassInput = cf.makeInput("Test", mainUi.uifont, 220, 150, 360, 32, nil)
  container:add(accountPassInput)
  
  local createButton = cf.makeButton("Create", mainUi.uifont, 190, 250, 0, 0.5, createCallback)
  container:add(createButton)
end


local function update(dt)

  -- newAccountPopup.text = newAccountPopup.text .. newAccountPopup.mainUi.inputtext
  -- newAccountPopup.mainUi.inputtext = ""
end


local function draw()
  
  local w = 640
  local h = 400
  local xoff = (1200-w)/2
  local yoff = (720-h)/2
  local yspace = 28
  local font = newAccountPopup.mainUi.uifont
  
  love.graphics.setColor(0.05, 0.1, 0.2, 0.5)
  love.graphics.rectangle("fill", xoff, yoff, w, h)
  love.graphics.setColor(0.9, 0.7, 0.4)
  love.graphics.rectangle("line", xoff, yoff, w, h)

  love.graphics.setColor(1, 1, 1)
  font:drawStringScaled("Create a New Account", xoff + 60, yoff + 20, 0.5, 0.5)

  font:drawStringScaled("Account Name:", xoff + 20, yoff + 110, 0.25, 0.25)
  font:drawStringScaled("Password:", xoff + 20, yoff + 150, 0.25, 0.25)


  container:draw(xoff, yoff)
end


local function mousePressed(button, mx, my)
  container:mousePressed(mx, my)
end


local function mouseReleased(button, mx, my)
  container:mouseReleased(mx, my)
end


local function mouseDragged(button, mx, my)
end


local function keyReleased(key, scancode, isrepeat)
  container:keyReleased(key, scancode, isrepeat)
end


newAccountPopup.init = init
newAccountPopup.update = update
newAccountPopup.draw = draw
newAccountPopup.mousePressed = mousePressed
newAccountPopup.mouseReleased = mouseReleased
newAccountPopup.mouseDragged = mouseDragged
newAccountPopup.keyReleased = keyReleased


return newAccountPopup
