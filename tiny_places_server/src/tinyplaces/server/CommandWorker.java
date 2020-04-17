package tinyplaces.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import tinyplaces.server.isomap.Client;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import tinyplaces.server.isomap.Mob;
import tinyplaces.server.isomap.Room;
import tinyplaces.server.isomap.actions.MapAction;
import tinyplaces.server.isomap.actions.Move;

/**
 * Worker class for map altering commands. This will be run in a thread of
 * its own.
 * 
 * @author Hj. Malthaner
 */
public class CommandWorker implements ServerWorker
{
    // network data queue
    private final List <ServerDataEvent> queue = new ArrayList();

    // client map
    private final Map <SocketChannel, Client> clients = new HashMap();
    
    @Override
    public void processData(Server server, SocketChannel socket, byte[] data, int bytes)
    {
        // Make a copy because the buffer can be overwritten any time later
        byte[] dataCopy = new byte[bytes];
        System.arraycopy(data, 0, dataCopy, 0, bytes);
        synchronized(queue)
        {
            queue.add(new ServerDataEvent(server, socket, dataCopy));
            queue.notify();
        }
    }

    @Override
    public void run()
    {
        while(true)
        {
            try
            {
                ServerDataEvent dataEvent;

                // Wait for data to become available
                synchronized (queue)
                {
                    while(queue.isEmpty())
                    {
                        try
                        {
                            queue.wait();
                        }
                        catch (InterruptedException iex)
                        {
                            System.err.println("CommandWorker: Interrupt during queue wait:" + iex);
                        }
                    }
                    dataEvent = (ServerDataEvent) queue.remove(0);
                }

                processCommands(dataEvent);
            }
            catch(Exception ex)
            {
                // report but keep flying
                Logger.getLogger(CommandWorker.class.getName()).log(Level.SEVERE, null, ex);                
            }
        }
    }
    
    
    private void processCommands(ServerDataEvent dataEvent)
    {
        String message = new String(dataEvent.data);
        String [] commands = message.split("\n");
        
        for(String command : commands)
        {
            processCommand(dataEvent, command + "\n");
        }
    }
    
    
    private void processCommand(ServerDataEvent dataEvent, String command)
    {
        if(command.startsWith("HELO"))
        {
            loginClient(dataEvent, command);
        }
        else if(command.startsWith("ADDM"))
        {
            addMob(dataEvent, command);
        }
        else if(command.startsWith("ADDP"))
        {
            addPlayer(dataEvent, command);
        }
        else if(command.startsWith("UPDM"))
        {
            updateMob(dataEvent, command);
        }
        else if(command.startsWith("DELM"))
        {
            deleteMob(dataEvent, command);
        }
        else if(command.startsWith("FIRE"))
        {
            fireProjectile(dataEvent, command);
        }
        else if(command.startsWith("GAME"))
        {
            startGame(dataEvent, command);
        }
        else if(command.startsWith("GBYE"))
        {
            logoutClient(dataEvent, command);
        }
        else if(command.startsWith("SAVE"))
        {
            saveMap(dataEvent, command);
        }
        else if(command.startsWith("LOAD"))
        {
            loadMap(dataEvent, command);
        }
        else if(command.startsWith("CHAT"))
        {
            sendChat(dataEvent, command);
        }
        else if(command.startsWith("MOVE"))
        {
            doMove(dataEvent, command);
        }
        else
        {
            Logger.getLogger(CommandWorker.class.getName()).log(Level.WARNING, "Received unknown command: '{0}'", command);
        }
    }
    
	
    private void loginClient(ServerDataEvent dataEvent, String command)
    {
        System.err.println("HELO from " + dataEvent.socket);
        
        clients.put(dataEvent.socket, new Client());
    }

    
    private void logoutClient(ServerDataEvent dataEvent, String command)
    {
        System.err.println("GBYE from " + dataEvent.socket);
        
        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
        Object test;
        
        test = room.removeMob(3, client.mob.id);
        if(test == null)
        {
            Logger.getLogger(CommandWorker.class.getName()).log(Level.WARNING, 
                    "Logout problem: client avatar was not in room.");
        }
        
        test = clients.remove(dataEvent.socket);
        if(test == null)
        {
            Logger.getLogger(CommandWorker.class.getName()).log(Level.WARNING, 
                    "Logout problem: client was not in list.");
        }
        
        Logger.getLogger(CommandWorker.class.getName()).log(Level.INFO, 
                "Remaining clients: {0}", clients.size());
    }

    
    private void addMob(ServerDataEvent dataEvent, String command)
    {
        System.err.println("ADDM from " + dataEvent.socket);
        
        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
        String [] parts = command.trim().split(",");

        Mob mob = room.makeMob(parts);
        
        roomcast(dataEvent.server,
                 "ADDM," + mob.id + "," + 
                         parts[1] + "," + parts[2] + "," + parts[3] + "," + parts[4] + "," + parts[5] + "," + parts[6] + "," +
                         "0\n",
                 room);
    }

    
    private void addPlayer(ServerDataEvent dataEvent, String command)
    {
        System.err.println("ADDP from " + dataEvent.socket);
        
        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
        String [] parts = command.trim().split(",");

        Mob mob = room.makeMob(parts);
        mob.type = Mob.TYPE_PLAYER;
        
        // set new player avatar
        client.mob = mob;
        
        // reply with ADDP to sender only

        String message = "ADDP," + mob.id + "," + parts[1] + "," + parts[2] + "," + parts[3] + "," + parts[4] + "," + parts[5] + "," + parts[6] + "\n";
        byte [] data = message.getBytes();
        
        SocketChannel senderSocket = dataEvent.socket;
        Server server = dataEvent.server;
        server.send(senderSocket, data);

        // for everyone else in the room it is an ADDM

        message = "ADDM," + mob.id + "," + 
                parts[1] + "," + parts[2] + "," + parts[3] + "," + parts[4] + "," + parts[5] + "," + parts[6] + "," +
                "2\n";
        data = message.getBytes();

        Set <SocketChannel> keys = clients.keySet();
        
        for(SocketChannel socket : keys)
        {
            if(socket != senderSocket)
            {
                Client c = clients.get(socket);
                if(c.getCurrentRoom() == room)
                {
                    server.send(socket, data);
                }
            }
        }
    }
    
    private void updateMob(ServerDataEvent dataEvent, String command)
    {
        System.err.println("UPDM from " + dataEvent.socket);
        
        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
        
        String [] parts = command.split(",");
        int id = Integer.parseInt(parts[1]);
        int layer = Integer.parseInt(parts[2]);
		
        Mob mob = room.getMob(layer, id);

        if(mob != null)
        {
            mob.tile = Integer.parseInt(parts[3]);
            mob.x = Integer.parseInt(parts[4]);
            mob.y = Integer.parseInt(parts[5]);
            mob.scale = Float.parseFloat(parts[6]);
            mob.color = parts[7].trim();

            roomcast(dataEvent.server, command, room);
        }
        else
        {
            Logger.getLogger(CommandWorker.class.getName()).log(Level.SEVERE, "Could not find mob for id={0}", id);
        }
    }
	

    private void deleteMob(ServerDataEvent dataEvent, String command)
    {
        System.err.println("DELM from " + dataEvent.socket);
        
        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
        
        String [] parts = command.split(",");
        int id = Integer.parseInt(parts[1].trim());
        int layer = Integer.parseInt(parts[2].trim());
		
        room.removeMob(layer, id);
        
        roomcast(dataEvent.server, command, room);
    }
	

    private void saveMap(ServerDataEvent dataEvent, String command) 
    {
        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
        
        String [] parts = command.split(",");
        String filename = parts[1].trim() + ".txt";
        room.save(filename);        
    }

    
    private void loadMap(ServerDataEvent dataEvent, String command) 
    {
        System.err.println("LOAD from " + dataEvent.socket);

        Client client = clients.get(dataEvent.socket);
        
        // Room room = client.getCurrentRoom();
        
        String [] parts = command.split(",");
        String filename = parts[1].trim();

        // check if the room is already loaded
        HashMap<String, Room> rooms = Room.getRooms();
        Room room = rooms.get(filename);

        if(room == null)
        {
            // room not loaded yet -> load it
            room = loadRoom(filename);

            room.setCommandWorker(this);
            room.setServer(dataEvent.server);
            
            client.setCurrentRoom(room);
            roomcast(dataEvent.server, "LOAD," + room.name + "," + room.backdrop + "," + filename + "\n", room);
            
            serveRoom(dataEvent, room);
        }
        else
        {
            // room is already loaded -> join it
            singlecast(dataEvent, "LOAD," + room.name + "," + room.backdrop + "," + filename + "\n");
            client.setCurrentRoom(room);
            serveRoom(dataEvent, room);
        }
    }

    
    private void serveRoom(ServerDataEvent dataEvent, Room room)
    {
        for(int layer = 1; layer < 6; layer += 2)
        {
            HashMap <Integer, Mob> map = room.getLayerMap(layer);
            Collection <Mob> mobs = map.values();
            
            for(Mob mob : mobs)
            {
                String command = makeAddMobCommand(mob, layer);

                singlecast(dataEvent, command);
            }
        }
    }

    
    private Room loadRoom(String filename)
    {
        String mapname = filename + ".txt";        
        File file = new File("maps", mapname);

        Room result = null;
        
        try 
        {
            BufferedReader reader = new BufferedReader(new FileReader(file));

            String version = reader.readLine();
            String roomname = reader.readLine();
            String backdrop = reader.readLine();

            result = new Room(roomname, backdrop);
            
            String line;
            while((line = reader.readLine()) != null)
            {
                System.err.println(line);
                
                String [] parts = ("ADDM," + line).split(",");
                
                result.makeMob(parts);
                
                // addMob(dataEvent, "ADDM," + line + ",0\n");
            }
            
            reader.close();
        }
        catch (IOException ex) 
        {
            Logger.getLogger(CommandWorker.class.getName()).log(Level.SEVERE, null, ex);
        }

        return result;
    }

    
    private void sendChat(ServerDataEvent dataEvent, String command)
    {
        System.err.println("CHAT from " + dataEvent.socket);

        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
        roomcast(dataEvent.server, command, room);
    }


    private void doMove(ServerDataEvent dataEvent, String command) 
    {
        System.err.println("MOVE from " + dataEvent.socket);

        String [] parts = command.trim().split(",");

        int id = Integer.parseInt(parts[1]);
        int layer = Integer.parseInt(parts[2]);
        int dx = Integer.parseInt(parts[3]);
        int dy = Integer.parseInt(parts[4]);
        int speed = 120;
        
        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
        Mob mob = room.getMob(layer, id);

        
        String pattern = "bounce";
        
        // todo - make some catalog of player and creatures with their properties
        if(mob.type == Mob.TYPE_PLAYER)
        {
            if(mob.tile == 9)
            {
                // spectres glide
                pattern = "glide";
            }
        }
        
        doMove(dataEvent, room, id, layer, dx, dy, speed, pattern);
    }

    public void doMove(ServerDataEvent dataEvent,
                       Room room, int id, int layer, int dx, int dy, int speed, String pattern)
    {
        Mob mob = room.getMob(layer, id);

        Move move = new Move(dataEvent, mob, layer, dx, dy, speed);
        
        // check and cancel former move ...
        List <MapAction> actions = room.getActions();
        ArrayList<MapAction> actionsCopy = new ArrayList<MapAction>(actions);
        
        for(MapAction action : actionsCopy)
        {
            if(action instanceof Move)
            {
                Move m = (Move)action;
                if(m.getMob().id == mob.id)
                {
                    System.err.println("Removing old move for mob id=" + mob.id);
                    
                    synchronized(actions)
                    {
                        actions.remove(m);
                    }
                }
            }
        }
        
        String command =
                "MOVE," +
                id + "," +
                layer + "," +
                dx + "," +
                dy + "," +
                speed + "," + 
                pattern + "\n"
                ;

        room.addAction(move);
        roomcast(room.getServer(), command, room);
    }

    
    public void transit(ServerDataEvent dataEvent, Mob mob, Room from, String roomname) 
    {
        from.removeMob(3, mob.id);
        
        String command = "LOAD," + roomname + "\n";
        
        loadMap(dataEvent, command);

        command =
            "ADDP," + 
            "3," + // layer
	    mob.tile + "," + // tile id
	    "360," + // x pos
	    "480," + // y pos
	    mob.scale + "," + // scale factor
            "1.0 1.0 1.0 1.0"; // color string

        addPlayer(dataEvent, command);
        
        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();

        List <Mob> mobs = room.makeMobGroup(20);
        addMobGroup(dataEvent, room, mobs, 3);    
    }
    
    
    private void startGame(ServerDataEvent dataEvent, String command) 
    {
        System.err.println("GAME from " + dataEvent.socket);

        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
    }
    
    private void fireProjectile(ServerDataEvent dataEvent, String command) 
    {
        System.err.println("FIRE from " + dataEvent.socket);

        String [] parts = command.split(",");
        
        int layer = Integer.parseInt(parts[1]);
        int type = Integer.parseInt(parts[2]);
        int dx = Integer.parseInt(parts[3]);
        int dy = Integer.parseInt(parts[4]);
        
        Client client = clients.get(dataEvent.socket);
        Room room = client.getCurrentRoom();
        
        fireProjectile(room, client.mob, layer, type, dx, dy);
    }
        
    public void fireProjectile(Room room, Mob mob, int layer, int type, int dx, int dy)   
    {
        int sx = mob.x;
        int sy = mob.y;

        int speed = 300;
        
        Mob projectile = room.makeMob(layer, type, sx, sy, 1.0f, "1 1 1 1", Mob.TYPE_PROJECTILE);
        
        String command = 
                "FIRE," +
                mob.id + "," +
                projectile.id + "," +
                layer + "," +
                type + "," +
                sx + "," +
                sy + "," +
                dx + "," +
                dy + "," +
                speed + "\n";

        Move move = new Move(null, projectile, layer, dx, dy, speed);
        room.addAction(move);
        
        roomcast(room.getServer(), command, room);
    }

    public void singlecast(ServerDataEvent dataEvent, String message)
    {
        byte [] data = message.getBytes();
        dataEvent.server.send(dataEvent.socket, data);
    }
    
    /**
     * Send a message to all clients in the given room
     * @param server
     * @param message 
     */
    public void roomcast(Server server, String message, Room room)
    {
        byte [] data = message.getBytes();
        Set <SocketChannel> keys = clients.keySet();
        
        for(SocketChannel socket : keys)
        {
            Client client = clients.get(socket);
            if(client.getCurrentRoom() == room)
            {
                server.send(socket, data);
            }
        }
    }

    
    /**
     * Send a message to all clients
     * @param server
     * @param message 
     */
    private void broadcast(Server server, String message)
    {
        byte [] data = message.getBytes();
        Set <SocketChannel> keys = clients.keySet();
        
        for(SocketChannel socket : keys)
        {
            server.send(socket, data);
        }
    }

    
    private String makeAddMobCommand(Mob mob, int layer)
    {
        String command =
                "ADDM," +
                mob.id + "," +
                layer + "," +
                mob.tile + "," +
                mob.x + "," +
                mob.y + "," +
                mob.scale + "," +
                mob.color + "," +
                mob.type +
                "\n";

        return command;
    }
    
    
    private void addMobGroup(ServerDataEvent dataEvent, Room room, Collection <Mob> mobs, int layer) 
    {
        for(Mob mob : mobs)
        {
            String command = makeAddMobCommand(mob, layer);
        
            roomcast(dataEvent.server, command, room);
        }
    }
}