
// #[test]
fn angle_diff() {
    use na::UnitComplex;
    use std::f32;

    let a = UnitComplex::from_angle(0.1*f32::consts::PI);
    let b = UnitComplex::from_angle(-0.1*f32::consts::PI);

    println!("{} * f32::consts::PI", b.angle_to(&a) / f32::consts::PI);
}

use util::*;

// #[test]
fn circ_intersect_test() {
    let c1 = Circle::new(0.,0.,3.);
    let c2 = Circle::new(6.,0.,3.);
    dump!(c1, c2);
    dump!(circ_intersect(c1,c2));
}