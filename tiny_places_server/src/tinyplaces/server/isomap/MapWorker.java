package tinyplaces.server.isomap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import tinyplaces.server.isomap.actions.MapAction;
import tinyplaces.server.isomap.actions.Move;

/**
 * Worker thread to process ongoing actions.
 * 
 * @author hjm
 */
public class MapWorker implements Runnable 
{
    @Override
    public void run() 
    {
        long lastTime = System.currentTimeMillis();
        
        while(true)
        {
            try
            {
                Thread.sleep(100);
            }
            catch(InterruptedException iex)
            {
                System.err.println("MapWorker: Interrupt during queue wait:" + iex);
            }
            
            long now = System.currentTimeMillis();
            int dt = (int)(now - lastTime);
            
            HashMap<String, Room> rooms = Room.getRooms();

            // System.err.println("MapWorker: dt=" + dt);
            // System.err.println("MapWorker: room count:" + rooms.size());

            for(Room room : rooms.values())
            {
                List <MapAction> actions = room.getActions();
                List <MapAction> killList = new ArrayList<MapAction>();

                // System.err.println("MapWorker: action count:" + actions.size());
                
                synchronized(actions)
                {
                    for(MapAction action : actions)
                    {
                       action.process(room, dt);
                       if(action.isDone())
                       {
                           killList.add(action);
                       }
                    }
                }

                for(MapAction action : killList)
                {
                    // hack - this needs some proper implementation
                    checkMapTransition(room, action);
                }
                
                synchronized(actions)
                {
                    actions.removeAll(killList);
                }
                
                room.aiCall();
            }
            
            lastTime = now;
        }
    }

    private void checkMapTransition(Room room, MapAction action) 
    {
        if("Lobby".equals(room.name) && (action instanceof Move))
        {
            Move move = (Move)action;
            int dx = move.x - 837;
            int dy = move.y - 168;
            int d2 = dx * dx + dy * dy;
            
            // monsters have no data event ... cannot transit to another room
            if(d2 < 250 && move.dataEvent != null)
            {
                room.transit(move.dataEvent, move.mob, "wasteland_and_pond");
            }
        }
    }
}