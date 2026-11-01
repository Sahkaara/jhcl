variable "region" {
  default = "eu-west-1"
  type    = string
}

resource "aws_instance" "web" {
  ami           = "ami-12345"
  instance_type = "t2.micro"
  count         = 3

  tags = {
    Name = "web"
    Env  = "prod"
  }

  security_groups = ["default", "web"]
}
