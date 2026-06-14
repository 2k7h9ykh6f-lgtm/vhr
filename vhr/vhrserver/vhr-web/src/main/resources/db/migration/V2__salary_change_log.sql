/*Table structure for table `salary_change_log` */

DROP TABLE IF EXISTS `salary_change_log`;

CREATE TABLE `salary_change_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `eid` int(11) DEFAULT NULL COMMENT '员工编号',
  `old_sid` int(11) DEFAULT NULL COMMENT '原薪资账套id',
  `new_sid` int(11) DEFAULT NULL COMMENT '新薪资账套id',
  `operator` varchar(255) DEFAULT NULL COMMENT '操作人',
  `create_time` datetime DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_scl_eid` (`eid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
