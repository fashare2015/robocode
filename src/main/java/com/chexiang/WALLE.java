package com.chexiang;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.GunTurnCompleteCondition;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;

import static robocode.util.Utils.normalAbsoluteAngleDegrees;
import static robocode.util.Utils.normalRelativeAngleDegrees;


public class WALLE extends AdvancedRobot{
    private static final double BULLET_POWER = 3;
    private final double CIRCLE = 360;
    private double enemyAbsBearing = 0; //敌方绝对bearing = 我方朝向 + 相对bearing
    private double myAbsBearingForEnemy;
    private boolean scaned = false;
    private double radarSpeed = CIRCLE/8;
    private Enemy preEnemy = new Enemy(), curEnemy = new Enemy();
    private boolean isMovingAhead = true;

    public void setEnemyAbsBearing(double enemyAbsBearing) {
        this.enemyAbsBearing = enemyAbsBearing;
        this.myAbsBearingForEnemy = reverseAngle(enemyAbsBearing);
    }

    public double getEnemyAbsBearing() {
        return enemyAbsBearing;
    }

    public void setRadarSpeed(double radarSpeed) {
        this.radarSpeed = radarSpeed;
    }

    private void reverseRadar(){
        setRadarSpeed(-radarSpeed);
    }

    @Override
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        while(true) {
            out.println("radarSpeed" + radarSpeed);
            setTurnRadarRight(radarSpeed);
            double radarHeading = getRadarHeading();
            if(scaned && !outOfEnemyRange(radarHeading) && outOfEnemyRange(radarHeading + radarSpeed)) {
                reverseRadar();
                scaned = false;
            }

//            ahead(100);
//            turnGunLeft(70);
//            back(100);
            execute();
        }
    }

    private boolean outOfEnemyRange(double radarHeading) {
        radarHeading = Mod(radarHeading);
        return Math.abs( minimizeTurn(radarHeading - getEnemyAbsBearing()) ) > CIRCLE/4;
    }

    @Override
    public void onHitWall(HitWallEvent event) {
        out.println(String.format("onHitWall\n getBearing: %f, getHeading: %f\n" +
                "sum: %f\n", event.getBearing(), getHeading(), Mod(event.getBearing() + getHeading() + CIRCLE / 4)));

//        turnAsReflect(getHeading(), Mod(event.getBearing() + getHeading() + CIRCLE / 4));     //为啥+90度？

    }

    private void turnAsReflect(double inAngle, double mirrorAngle) {
        turnToTargetAngle( doReflect(inAngle, mirrorAngle) );
        setAhead(50);
        execute();
    }

    private double doReflect(double inAngle, double mirrorAngle) {
        if(Math.abs( minimizeTurn(inAngle - mirrorAngle) ) > CIRCLE/4)
            mirrorAngle = reverseAngle(mirrorAngle);
//        out.println(String.format("doReflect\n inAngle: %f, mirrorAngle: %f\n" +
//                "outAngle: %f\n", inAngle, mirrorAngle, Mod(2*mirrorAngle - inAngle)));
        return Mod(2*mirrorAngle - inAngle);
    }


    @Override
    public void onHitRobot(HitRobotEvent event) {
//        turnAsReflect(getHeading(), Mod(event.getBearing() + getHeading() + CIRCLE / 4));
        reverseHeading();
//        if (Math.abs(event.getBearing()) < 10)
//            fire(1);
//        if (event.isMyFault())
//            turnRight(20);
        //back(100);
    }

    private void reverseHeading() {
        isMovingAhead = !isMovingAhead;
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        scan();
        dodgeTheBullet();
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        out.print("onScannedRobot: heading = " + event.getHeading());
        curEnemy = new Enemy(event);
        scaned = true;
        setEnemyAbsBearing(Mod(this.getHeading() + event.getBearing()));
        reverseRadar();

        aimAndFire();

        if(enemyMayFire(event))
            dodgeTheBullet();
        else {
            keepCrossWise();   //与敌方炮口保持横向(垂直)，以便躲避子弹
            //setTurnGunRight(angle);
        }

        preEnemy = new Enemy(event);
        execute();
    }

    private void aimAndFire() {
        double angleForGunTurn = getAngleForGunTurn();
        setTurnGunRight(angleForGunTurn);
        if(Math.abs(angleForGunTurn) < 10) {
            waitFor(new GunTurnCompleteCondition(this));
            setFire(BULLET_POWER);
        }
    }

    private double getAngleForGunTurn() {
        //out.println("getAngleForGunTurn: adjustAngle: " + getAdjustAngle());
        return minimizeTurn((getEnemyAbsBearing() - this.getGunHeading()) + getAdjustAngle());
    }

    private double getAdjustAngle() {   //正弦定理
        double  bulletAngle = Mod(myAbsBearingForEnemy - curEnemy.getHeading()),    //sin(bulletAngle) 自带正负
                bulletSpeed = Rules.getBulletSpeed(BULLET_POWER),
                enemySpeed = curEnemy.getVelocity();
        double enemyAngle = asin(Math.abs(sin(bulletAngle)) * (enemySpeed / bulletSpeed));

        out.println(String.format("getAdjustAngle: myAbsBearingForEnemy = %f, curEnemy.getHeading = %f\n",
                myAbsBearingForEnemy, curEnemy.getHeading()));

        out.println(String.format("getAdjustAngle: bulletAngle = %f, bulletSpeed = %f, enemySpeed = %f\n" +
                "enemyAngle = %f", bulletAngle, bulletSpeed, enemySpeed, enemyAngle));

        //敌人前进方向转向对准你，所花的角度 bulletAngle <180,则在你右边
        return bulletAngle < 180? enemyAngle: -enemyAngle;
        //return enemyAngle;
    }

    private double asin(double x) {
        return Math.toDegrees(Math.asin(x));
    }

    private double sin(double angle) {
        return Math.sin(Math.toRadians(angle));
    }

    private void keepCrossWise() {
        out.println(String.format("getBearing: %f\n", getEnemyAbsBearing()));
        turnToTargetAngle( Mod(getEnemyAbsBearing() + CIRCLE/4) );
    }

    /**
     * 车轮转到指定角度
     * @param targetAngle 指定角度 [0,360)
     */
    private void turnToTargetAngle(double targetAngle) {
        out.println(String.format("targetAngle: %f, Heading: %f\n", targetAngle, getHeading()));
        setTurnRight(minimizeTurn(targetAngle - getHeading()));
    }

    private void dodgeTheBullet() {
        //setAhead(50);
        move();
    }

    private boolean enemyMayFire(ScannedRobotEvent event) {
        out.println("on enemyMayFire");
        return aimedByEnemy(event.getHeading()) && costEnergyForFire(preEnemy.getEnergy() - event.getEnergy());
    }

    private boolean costEnergyForFire(double changedEnergy) {
        out.println("on costEnergyForFire" + changedEnergy);
        return changedEnergy>=0.1 && changedEnergy<=3;
    }


    private boolean aimedByEnemy(double enemyHeading) {
//        double a = Mod(enemyHeading), b = Mod(this.getEnemyAbsBearing() + CIRCLE/2);
//        out.println(String.format("on aimedByEnemy:\n a = %f, b = %f\n a+b = %f", a, b, a+b));
//        return Math.abs(a - b) < 5;
        return true;
    }

    private double Mod(double angle){   //取模,规整为[0~360)
        return normalAbsoluteAngleDegrees(angle);
//        double tmp = angle + CIRCLE * 999999;
//        return tmp - ((int)(tmp/CIRCLE))*CIRCLE;
    }

    private double minimizeTurn(double angle){  //[0~360) => [-180~180)
        return normalRelativeAngleDegrees(angle);
//        angle = Mod(angle);
//        return angle>CIRCLE/2? angle-CIRCLE: angle;
    }

    private double reverseAngle(double angle) {
        return Mod(angle + CIRCLE/2);
    }

    @Override
    public void onBulletHit(BulletHitEvent event) {
        preEnemy.setEnergy(event.getEnergy());
    }

    @Override
    public void onBulletMissed(BulletMissedEvent event) {
    }

    private void move(){
        if(isMovingAhead)
            setAhead(50);
        else
            setBack(50);
    }


    private class Enemy{
        private String name;
        private double energy;
        private double heading;
        private double bearing;
        private double distance;
        private double velocity;

        public Enemy(String name, double energy, double heading, double bearing, double distance, double velocity) {
            this.name = name;
            this.energy = energy;
            this.heading = heading;
            this.bearing = bearing;
            this.distance = distance;
            this.velocity = velocity;
        }

        public Enemy(ScannedRobotEvent event){
            this(
                    event.getName(),
                    event.getEnergy(),
                    event.getHeading(),
                    event.getBearing(),
                    event.getDistance(),
                    event.getVelocity()
            );
        }

        public Enemy() {
            this("", 0, 0, 0, 0, 0);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getEnergy() {
            return energy;
        }

        public void setEnergy(double energy) {
            this.energy = energy;
        }

        public double getHeading() {
            return heading;
        }

        public void setHeading(double heading) {
            this.heading = heading;
        }

        public double getBearing() {
            return bearing;
        }

        public void setBearing(double bearing) {
            this.bearing = bearing;
        }

        public double getDistance() {
            return distance;
        }

        public void setDistance(double distance) {
            this.distance = distance;
        }

        public double getVelocity() {
            return velocity;
        }

        public void setVelocity(double velocity) {
            this.velocity = velocity;
        }




    }

}
