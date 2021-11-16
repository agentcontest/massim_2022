package massim.game.environment;

import massim.protocol.data.Position;
import massim.util.RNG;

import org.json.JSONArray;
import org.json.JSONObject;

import static org.junit.Assert.assertNotNull;

import java.util.*;

public class GridTest {
    private JSONObject gridjson;

    @org.junit.Before
    public void setUp() {
        RNG.initialize(17);
        
        this.gridjson = new JSONObject()
                .put("height", 70)
                .put("width", 70)
                .put("instructions", new JSONArray("[[\"cave\", 0.45, 9, 5, 4]]"))
                .put("goals", new JSONObject("{\"number\" : 3,\"size\" : [1,2],\"moveProbability\" : 0}"))
                .put("roleZones", new JSONObject("{\"number\" : 3,\"size\" : [1,2]}"));
    }

    @org.junit.Test
    public void findRandomFreeClusterPosition() {
        this.gridjson.put("height", 5);
        this.gridjson.put("width", 5);
        System.out.println(this.gridjson.toString());
        Grid grid = new Grid(this.gridjson, 10);

        printGridTerrain(grid);
        
        System.out.println("Testing cluster size 1");
        RNG.initialize(15);
        ArrayList<Position> cluster = grid.findRandomFreeClusterPosition(1);
        assertNotNull(cluster);
        assert(cluster.size()==1);

        assert grid.isUnblocked(cluster.get(0));
//        assert(cluster.get(0).toString().equals("(2,2)"));

        System.out.println("Testing cluster size 3");
        RNG.initialize(15);
        printGridTerrain(grid);
        ArrayList<Position> cluster3 = grid.findRandomFreeClusterPosition(3);
        assertNotNull(cluster3);
        assert(cluster3.size()==3);

        assert grid.isUnblocked(cluster3.get(0));
        assert grid.isUnblocked(cluster3.get(1));
        assert grid.isUnblocked(cluster3.get(2));
    }

    private void printGridTerrain(Grid grid){
        for (int x=0; x < grid.getDimX(); x++){
            System.out.println(" ");
            for (int y=0; y < grid.getDimY(); y++)
                System.out.print(" "+String.format("%1$5s",grid.isUnblocked(new Position(x, y))? "O" : "X"));
        }
        System.out.println(" ");
    }
//    private void printGridAgents(Grid grid){
//        for (int x=0; x < grid.getDimX(); x++){
//            System.out.println(" ");
//            for (int y=0; y < grid.getDimY(); y++){
//                System.out.print(" "+String.format("%1$5s",grid.getThings(new Position(x, y)).toString()));
//            }
//        }
//        System.out.println(" ");
//    }
}