
use na::{UnitComplex, Translation2, Vector2, Point2, Isometry2, Rotation2, Vector1, Real};
use nphysics2d::object::{self, RigidBody, Sensor, SensorCollisionGroups};
use nphysics2d::world::{self};
use ncollide::shape::{Cuboid, Segment};
use assert_approx_eq;

use std::f32;

use util;

use module::*;

pub type World = world::World<f32>;
pub type RigidBodyHandle = object::RigidBodyHandle<f32>;
pub type SensorHandle = object::SensorHandle<f32>;

pub const HALF_WIDTH: f32 = 0.3;
pub const HALF_HEIGHT: f32 = 0.3;

lazy_static! {
    //TODO: Why isn't the ISO made with the VEC and ROT??
    pub static ref FRONT_SENSOR_VEC: Vector2<f32> = Vector2::new(HALF_WIDTH,0.);
    pub static ref FRONT_SENSOR_ROT: UnitComplex<f32> = UnitComplex::from_angle(0.);
    pub static ref FRONT_SENSOR_ISO: Isometry2<f32> = Isometry2::new(Vector2::new(HALF_WIDTH,0.), UnitComplex::from_angle(0.).angle());
    pub static ref LEFT_SENSOR_VEC: Vector2<f32> = Vector2::new(0., -HALF_HEIGHT);
    pub static ref LEFT_SENSOR_ROT: UnitComplex<f32> = UnitComplex::from_cos_sin_unchecked(0., -1.);
    pub static ref LEFT_SENSOR_ISO: Isometry2<f32> = Isometry2::new(Vector2::new(0., -HALF_HEIGHT), UnitComplex::from_cos_sin_unchecked(0., -1.).angle());
    pub static ref RIGHT_SENSOR_VEC: Vector2<f32> = Vector2::new(0., HALF_HEIGHT);
    pub static ref RIGHT_SENSOR_ROT: UnitComplex<f32> = UnitComplex::from_cos_sin_unchecked(0., 1.);
    pub static ref RIGHT_SENSOR_ISO: Isometry2<f32> = Isometry2::new(Vector2::new(0., HALF_HEIGHT), UnitComplex::from_cos_sin_unchecked(0., 1.).angle());
    
}
pub const SONAR_DEVIANCE: f32 = ((15. / 360.) * 2. * f32::consts::PI) / 2.; //in rad

macro_rules! sonar_deviance_cmplx {
    () => {  UnitComplex::from_angle(SONAR_DEVIANCE) };
}

pub type Wall = (Point2<f32>, Point2<f32>);

pub struct Robot<T: Module> {
    hardware: RobotHardware,
    module: T
}

impl<T: Module> Robot<T> {
    pub fn new(module: T, world: &mut World, walls: Box<[Wall]>) -> Self {
        Self {
            hardware: RobotHardware::new(world, walls),
            module: module
        }
    }
    pub fn update(&mut self, dt: f32) {
        {
            let b = self.hardware.rigid_body.borrow();
            let p = b.position();
            // dump!(p.translation.vector, p.rotation.angle() / f32::pi());
        }
        let hardware = &mut self.hardware;
        let (lw_speed, rw_speed) = self.module.update(hardware);
        hardware.update(dt, lw_speed, rw_speed);
    }
    pub fn hardware(&self) -> &RobotHardware {
        &self.hardware
    }
}

pub struct RobotHardware {
    walls: Box<[Wall]>,
    rigid_body: RigidBodyHandle,
    // left_hit_sensor: SensorHandle,
    // right_hit_sensor: SensorHandle,
    front_dis_sensor: (SensorHandle, SensorHandle),
    left_dis_sensor: (SensorHandle, SensorHandle),
    right_dis_sensor: (SensorHandle, SensorHandle),
}

impl RobotHardware {
    pub fn new(world: &mut World, walls: Box<[Wall]>) -> Self {
        let mut rb = RigidBody::new_dynamic(Cuboid::new(Vector2::new(HALF_WIDTH ,HALF_HEIGHT)), 1.0, 0.3, 0.6);
        rb.set_deactivation_threshold(None);

        let rigid_body = world.add_rigid_body(rb);

        let mut sensor_groups_ws = SensorCollisionGroups::new();
        sensor_groups_ws.enable_interaction_with_static();

        macro_rules! add_sensor {
            ($geom: expr, $iso: expr) => {{
                let mut sensor = Sensor::new($geom, Some(rigid_body.clone()));
                // let trans = Translation2::from_vector($trans);
                // let rel_pos = Isometry2::from_parts(trans, $rot);
                sensor.set_relative_position($iso);
                sensor.set_collision_groups(sensor_groups_ws);
                world.add_sensor(sensor)
            }};
        }

        macro_rules! add_sonar {
            ($geom: expr, $iso: expr) => {
                (add_sensor!($geom.clone(), Isometry2::from_parts(($iso).translation, ($iso).rotation * sonar_deviance_cmplx!())),
                 add_sensor!($geom, Isometry2::from_parts(($iso).translation, ($iso).rotation * sonar_deviance_cmplx!().conjugate())))
            };
        }

        let hit_sensor_len = 0.04;
        let hit_sensor_geom = Segment::new(Point2::new(0.,0.), Point2::new(0., hit_sensor_len));

        let dis_sensor_geom = Segment::new(Point2::new(0.,0.), Point2::new(100., 0.));

        Self { 
            walls: walls,
            // left_hit_sensor: add_sensor!(hit_sensor_geom.clone(), Vector2::new(0.,0.), UnitComplex::from_angle(0.)),
            // right_hit_sensor: add_sensor!(hit_sensor_geom, Vector2::new(0.,0.), UnitComplex::from_angle(0.)),
            front_dis_sensor: add_sonar!(dis_sensor_geom.clone(), *FRONT_SENSOR_ISO),
            left_dis_sensor: add_sonar!(dis_sensor_geom.clone(), *LEFT_SENSOR_ISO),
            right_dis_sensor: add_sonar!(dis_sensor_geom, *RIGHT_SENSOR_ISO),
            rigid_body: rigid_body
        }
    }

    pub fn update(&mut self, dt: f32, lw_speed: f32, rw_speed: f32) {
        // let delta = self.left_wheel_speed - self.right_wheel_speed;
        // let total = self.left_wheel_speed + self.right_wheel_speed;

        // let mut body = self.rigid_body.borrow_mut();

        // let (dx, dy, dangle) = {
        //     let position = body.position();

        //     let dircos = position.rotation.cos_angle();
        //     let dirsin = position.rotation.sin_angle();

        //     if delta == 0.0 {
        //         let speed = self.left_wheel_speed; //= self.module.right_speed();
        //         let distance = speed;
        //         (distance*dircos, distance*dirsin, 0.0)
        //     } else {
        //         let eff1 = (HALF_WIDTH*total)/(delta);
        //         let eff2 = (delta)/(HALF_WIDTH*2.) + position.rotation.angle();

        //         let dx = eff1 * ((eff2).sin() - dirsin);
        //         let dy = -eff1 * ((eff2).cos() - dircos);
        //         let dangle = (delta)/(HALF_WIDTH*2.);

        //         (dx, dy, dangle)
        //     }
        // };

        // println!("{}, {}, {}", dx, dy, dangle);

        let mut body = self.rigid_body.borrow_mut();

        let iso = util::pos_delta(HALF_WIDTH, dt, lw_speed, rw_speed);
        // dump!(HALF_WIDTH, dt, lw_speed, rw_speed);
        // dump!(iso);
        let rot = body.position().rotation;

        // println!("dx: {}, dy: {}, dangle: {}", dx, dy, dangle);

        body.set_lin_vel((rot * iso.translation.vector) / dt);
        body.set_ang_vel(Vector1::new(iso.rotation.angle()) / dt);

        // body.append_transformation(&iso);

        // body.append_translation(&Translation2::new(dx, dy));
        // body.append_rotation(&UnitComplex::from_angle(dangle));
        // println!("{}", body.is_active());
    }

    pub fn rigid_body(&self) -> &RigidBodyHandle { &self.rigid_body }
    // pub fn left_hit_sensor(&self) -> &SensorHandle { &self.left_hit_sensor }
    // pub fn right_hit_sensor(&self) -> &SensorHandle { &self.right_hit_sensor }
    pub fn front_dis_sensor(&self) -> &(SensorHandle, SensorHandle) { &self.front_dis_sensor }
    pub fn left_dis_sensor(&self) -> &(SensorHandle, SensorHandle) { &self.left_dis_sensor }
    pub fn right_dis_sensor(&self) -> &(SensorHandle, SensorHandle) { &self.right_dis_sensor }

    fn scan(&self, sensor_rel_vec: Vector2<f32>, sensor_rel_rot: UnitComplex<f32>) -> f32 {
        let (sensor_pos, sensor_rot) = {
            let body = self.rigid_body.borrow();
            let pos = body.position();
        // println!("{} * {} = {}", pos.rotation, sensor_rel_rot, pos.rotation * sensor_rel_rot);
            let iso = Isometry2::from_parts(Translation2::from_vector(sensor_rel_vec), sensor_rel_rot);
            (Point2::from_coordinates(pos.translation.vector) + (pos.rotation * sensor_rel_vec), sensor_rel_rot * pos.rotation)
        };
        let mut distance = f32::INFINITY;
        // println!("walls: {}, robot_x: {}, robot_y: {}, robot_rot: {}", self.walls.len(), robot_x, robot_y, robot_rot.angle().to_degrees());
        for wall in &*self.walls {
            let wall_vec = wall.1 - wall.0;
            let wall_vec_rot = wall_vec[1].atan2(wall_vec[0]);
            
            let neg_trans = Translation2::from_vector(-wall.0.coords);
            let neg_rot = Rotation2::new(-wall_vec_rot);
            let relative = neg_rot * (neg_trans * sensor_pos);

            let wall_width = (neg_rot * wall_vec)[0];

            if relative[0] < 0. || relative[0] > wall_width {
                continue
            }

            let new_distance = relative[1].abs();
            if new_distance < 0. {
                continue
            }

            let sensor_line_rot = if relative[1] < 0. {
                wall_vec_rot + 0.5 * f32::consts::PI
            } else if relative[1] > 0. {
                wall_vec_rot - 0.5 * f32::consts::PI
            } else {
                continue
            };

            let rot_diff = UnitComplex::from_angle(sensor_line_rot).angle_to(&sensor_rot).abs();

            if rot_diff > SONAR_DEVIANCE {
                continue
            }

            if new_distance < distance {
                distance = new_distance
            }

            // println!("wall.0: {}, wall_vec: {}, wall.1: {}, trans: {}, rot: {}, relative: {}, dis: {}", 
            //          wall.0, Point2::from_coordinates(wall_vec), wall.1, 0, 0, relative, new_distance);
        }  
        distance
    }
}

impl Hardware for RobotHardware {
    const HALF_WIDTH: f32 = HALF_WIDTH;
    const SCAN_SENSORS: usize = 3;
    const SONAR_DEVIANCE: f32 = SONAR_DEVIANCE;
    fn scan(&self, id: usize) -> (f32, Isometry2<f32>) {
        match id {
            0 => (self.scan(*FRONT_SENSOR_VEC, *FRONT_SENSOR_ROT), *FRONT_SENSOR_ISO),
            1 => (self.scan(*LEFT_SENSOR_VEC, *LEFT_SENSOR_ROT), *LEFT_SENSOR_ISO),
            2 => (self.scan(*RIGHT_SENSOR_VEC, *RIGHT_SENSOR_ROT), *RIGHT_SENSOR_ISO),
            _ => panic!("Invalid sensor ID")
        }
    }
    // fn scan_left(&self) -> f32 {
    //     self.scan(*LEFT_SENSOR_VEC, *LEFT_SENSOR_ROT)
    // }
    // fn scan_right(&self) -> f32 {
    //     self.scan(*RIGHT_SENSOR_VEC, *RIGHT_SENSOR_ROT)
    // }
    // fn scan_front(&self) -> f32 {
    //     self.scan(*FRONT_SENSOR_VEC, *FRONT_SENSOR_ROT)
    // }
    fn hits(&self, id: usize) -> bool {
        false
    }
}

// struct SensorUpdatesHandler {
//     hardware: Rc<RefCell<RobotHardware>>
// }

// impl ProximityHandler<Point2<f32>, Isometry2<f32>, WorldObject<f32>> for SensorUpdatesHandler {
//     fn handle_proximity(&mut self, o1: &WorldCollisionObject<f32>, o2: &WorldCollisionObject<f32>,
//                         _: Proximity, new_proximity: Proximity) {
//         let hitting = match new_proximity {
//             Proximity::WithinMargin | Proximity::Intersecting => { true },
//             Proximity::Disjoint => { false }
//         };
//         let mut hardware = self.hardware.borrow_mut();
//         if o1.uid == WorldObject::sensor_uid(hardware.left_hit_sensor()) ||  o1.uid == WorldObject::sensor_uid(hardware.right_hit_sensor()) {
//             hardware.hits_front = hitting;
//             println!("Front hitting? -> {}", hitting)
//         }
//     }
// }