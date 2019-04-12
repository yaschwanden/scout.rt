export default class Dimension {

  constructor(vararg, height) {
    if (vararg instanceof Dimension) {
      this.width = vararg.width;
      this.height = vararg.height;
    } else {
      this.width = vararg || 0;
      this.height = height || 0;
    }
  }

  toString() {
    return 'Dimension[width=' + this.width + ' height=' + this.height + ']';
  };

  equals(o) {
    if (!o) {
      return false;
    }
    return (this.width === o.width && this.height === o.height);
  };

  clone() {
    return new Dimension(this.width, this.height);
  };

  subtract(insets) {
    return new Dimension(
      this.width - insets.horizontal(),
      this.height - insets.vertical());
  };

  add(insets) {
    return new Dimension(
      this.width + insets.horizontal(),
      this.height + insets.vertical());
  };

  floor() {
    return new Dimension(Math.floor(this.width), Math.floor(this.height));
  };

  ceil() {
    return new Dimension(Math.ceil(this.width), Math.ceil(this.height));
  };
}
